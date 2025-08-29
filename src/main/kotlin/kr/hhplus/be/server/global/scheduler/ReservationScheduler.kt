package kr.hhplus.be.server.global.scheduler

import kr.hhplus.be.server.domain.auth.service.TokenManager
import kr.hhplus.be.server.domain.auth.service.QueueService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import org.springframework.context.ApplicationEventPublisher
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockStrategy
import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

@Component
@ConditionalOnProperty(name = ["app.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class ReservationScheduler(
    private val reservationService: ReservationService,
    private val tokenManager: TokenManager,
    private val queueService: QueueService,
    private val selloutRankingService: SelloutRankingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val distributedLock: DistributedLock
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(ReservationScheduler::class.java)
        private const val RESERVATION_CLEANUP_BATCH_SIZE = 100
    }
    
    private val reservationCleanupCount = AtomicInteger(0)
    private val tokenActivationCount = AtomicInteger(0)
    private val tokenCleanupCount = AtomicInteger(0)

    @Scheduled(fixedRate = 30000)
    fun expireReservations() {
        distributedLock.executeWithLock(
            lockKey = "scheduler:reservation:expire",
            strategy = LockStrategy.PUB_SUB,
            lockTimeoutMs = 25000L,
            waitTimeoutMs = 5000L
        ) {
            val startTime = System.currentTimeMillis()
            val currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            
            var cleanedCount = 0
            var batch = 1
            
            while (true) {
                val expiredReservations = reservationService.getExpiredReservations(RESERVATION_CLEANUP_BATCH_SIZE)
                if (expiredReservations.isEmpty()) break
                
                expiredReservations.forEach { reservation ->
                    try {
                        val cancelledReservation = reservationService.cancelReservationBySystem(
                            reservation.reservationId, 
                            "예약 시간 만료"
                        )
                        
                        applicationEventPublisher.publishEvent(
                            ReservationCancelledEvent(
                                reservationId = cancelledReservation.reservationId,
                                userId = cancelledReservation.userId,
                                concertId = cancelledReservation.concertId,
                                seatId = cancelledReservation.seatId,
                                cancelReason = "예약 시간 만료",
                                isExpired = true
                            )
                        )
                        cleanedCount++
                    } catch (e: Exception) {
                        logger.warn("예약 만료 처리 실패: reservationId={}, error={}", reservation.reservationId, e.message)
                    }
                }
                batch++
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            reservationCleanupCount.addAndGet(cleanedCount)
            
            if (cleanedCount > 0) {
                logger.info("✅ 만료된 예약 정리 완료: {}건 ({}ms)", cleanedCount, elapsed)
            }
        }
    }

    @Scheduled(fixedRate = 10000)
    fun activateTokens() {
        distributedLock.executeWithLock(
            lockKey = "scheduler:token:activate",
            strategy = LockStrategy.PUB_SUB,
            lockTimeoutMs = 8000L,
            waitTimeoutMs = 2000L
        ) {
            val startTime = System.currentTimeMillis()
            queueService.processNext()
            val elapsed = System.currentTimeMillis() - startTime
            logger.info("🚀 토큰 활성화 완료 ({}ms)", elapsed)
        }
    }

    @Scheduled(fixedRate = 60000)
    fun cleanupExpiredTokens() {
        distributedLock.executeWithLock(
            lockKey = "scheduler:token:cleanup",
            strategy = LockStrategy.PUB_SUB,
            lockTimeoutMs = 55000L,
            waitTimeoutMs = 5000L
        ) {
            val startTime = System.currentTimeMillis()
            tokenManager.cleanupExpiredTokens()
            val elapsed = System.currentTimeMillis() - startTime
            logger.info("🧹 만료 토큰 정리 완료 ({}ms)", elapsed)
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    fun updateSelloutRankings() {
        try {
            distributedLock.executeWithLock(
                lockKey = "scheduler:sellout:update",
                strategy = LockStrategy.PUB_SUB,
                lockTimeoutMs = 290000L,
                waitTimeoutMs = 10000L
            ) {
                val startTime = System.currentTimeMillis()
                selloutRankingService.rebuildSelloutRanking()
                val elapsed = System.currentTimeMillis() - startTime
                logger.info("📊 매진 랭킹 업데이트 완료 ({}ms)", elapsed)
            }
        } catch (e: Exception) {
            logger.error("매진 랭킹 업데이트 실패", e)
        }
    }
}