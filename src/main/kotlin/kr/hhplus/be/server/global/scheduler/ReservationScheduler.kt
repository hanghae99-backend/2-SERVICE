package kr.hhplus.be.server.global.scheduler

import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.lock.DistributedLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val queueManager: QueueManager,
    private val domainEventPublisher: DomainEventPublisher,
    private val distributedLock: DistributedLock
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(ReservationScheduler::class.java)
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
    
    @Value("\${app.scheduler.reservation.cleanup.interval:60000}")
    private var reservationCleanupInterval: Long = 60000
    
    @Value("\${app.scheduler.queue.process.interval:5000}")
    private var queueProcessInterval: Long = 5000
    
    @Value("\${app.scheduler.token.cleanup.interval:30000}")
    private var tokenCleanupInterval: Long = 30000
    
    // 성능 모니터링
    private val reservationCleanupCount = AtomicInteger(0)
    private val queueProcessCount = AtomicInteger(0)
    private val tokenCleanupCount = AtomicInteger(0)
    private val errorCount = AtomicInteger(0)
    
    /**
     * 만료된 예약 정리 - 분산 락을 사용하여 중복 실행 방지
     */
    @Scheduled(fixedRateString = "\${app.scheduler.reservation.cleanup.interval:60000}")
    fun cleanupExpiredReservations() {
        distributedLock.executeWithLock(
            lockKey = "scheduler:reservation:cleanup",
            lockTimeoutMs = 50000L,
            waitTimeoutMs = 10000L
        ) {
            try {
                val startTime = System.currentTimeMillis()
                val cleanedCount = reservationService.cleanupExpiredReservations()
                val elapsed = System.currentTimeMillis() - startTime
                
                reservationCleanupCount.addAndGet(cleanedCount)
                
                if (cleanedCount > 0) {
                    logger.info("✅ 만료된 예약 정리 완료: {}건 ({}ms)", cleanedCount, elapsed)
                } else {
                    logger.debug("🔍 만료된 예약 없음 ({}ms)", elapsed)
                }
                
                // 처리 시간이 오래 걸린 경우 경고
                if (elapsed > 10000) {
                    logger.warn("⚠️ 예약 정리 처리 시간 초과: {}ms", elapsed)
                }
                
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                logger.error("❌ 만료된 예약 정리 중 오류 발생", e)
                throw e
            }
        }
    }
    
    /**
     * 대기열 처리 - 활성 토큰 생성 및 만료 토큰 정리
     */
    @Scheduled(fixedRateString = "\${app.scheduler.queue.process.interval:5000}")
    fun processQueue() {
        distributedLock.executeWithLock(
            lockKey = "scheduler:queue:process",
            lockTimeoutMs = 4000L,
            waitTimeoutMs = 1000L
        ) {
            try {
                val startTime = System.currentTimeMillis()
                val currentTime = LocalDateTime.now().format(timeFormatter)
                
                // 1. 만료된 토큰 정리
                val expiredTokenCount = tokenLifecycleManager.cleanupExpiredTokens()
                
                // 2. 대기열 자동 처리
                val processedTokenCount = queueManager.processQueueAutomatically()
                
                val elapsed = System.currentTimeMillis() - startTime
                queueProcessCount.incrementAndGet()
                
                if (expiredTokenCount > 0 || processedTokenCount > 0) {
                    logger.info("🔄 대기열 처리 완료 [{}] - 만료: {}개, 활성화: {}개 ({}ms)", 
                        currentTime, expiredTokenCount, processedTokenCount, elapsed)
                } else {
                    logger.debug("🔍 대기열 처리 완료 [{}] - 변경사항 없음 ({}ms)", currentTime, elapsed)
                }
                
                // 통계 정보 주기적 출력 (매 10회마다)
                if (queueProcessCount.get() % 10 == 0) {
                    logSchedulerStatistics()
                }
                
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                logger.error("❌ 대기열 자동 처리 중 오류 발생", e)
                throw e
            }
        }
    }
    
    /**
     * 만료된 토큰 정리 - 더 빠른 정리를 위한 별도 스케줄
     */
    @Scheduled(fixedRateString = "\${app.scheduler.token.cleanup.interval:30000}")
    fun cleanupExpiredTokens() {
        distributedLock.executeWithLock(
            lockKey = "scheduler:token:cleanup",
            lockTimeoutMs = 25000L,
            waitTimeoutMs = 5000L
        ) {
            try {
                val startTime = System.currentTimeMillis()
                val cleanedCount = tokenLifecycleManager.cleanupExpiredTokens()
                val elapsed = System.currentTimeMillis() - startTime
                
                tokenCleanupCount.addAndGet(cleanedCount)
                
                if (cleanedCount > 0) {
                    logger.info("🧹 만료된 토큰 정리 완료: {}개 ({}ms)", cleanedCount, elapsed)
                } else {
                    logger.debug("🔍 만료된 토큰 없음 ({}ms)", elapsed)
                }
                
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                logger.error("❌ 만료된 토큰 정리 중 오류 발생", e)
                throw e
            }
        }
    }
    
    /**
     * 시스템 상태 정기 점검 - 5분마다
     */
    @Scheduled(fixedRate = 300000) // 5분
    fun systemHealthCheck() {
        try {
            val statistics = getSchedulerStatistics()
            logger.info("📊 스케줄러 상태 점검: {}", statistics)
            
            // 오류율이 높은 경우 경고
            if (statistics.errorRate > 10.0) {
                logger.warn("⚠️ 스케줄러 오류율 높음: {}%", String.format("%.2f", statistics.errorRate))
            }
            
            // 이벤트 시스템 통계
            val eventStats = domainEventPublisher.getEventStatistics()
            logger.info("📈 이벤트 시스템 통계: {}", eventStats)
            
            // 분산 락 통계
            val lockStats = distributedLock.getLockStatistics()
            logger.info("🔒 분산 락 통계: {}", lockStats)
            
        } catch (e: Exception) {
            logger.error("❌ 시스템 상태 점검 중 오류 발생", e)
        }
    }
    
    /**
     * 캐시 갱신 스케줄러 - 10분마다
     */
    @Scheduled(fixedRate = 600000) // 10분
    fun refreshCaches() {
        distributedLock.executeWithLock(
            lockKey = "scheduler:cache:refresh",
            lockTimeoutMs = 60000L,
            waitTimeoutMs = 10000L
        ) {
            try {
                val startTime = System.currentTimeMillis()
                
                // 캐시 갱신 로직이 있다면 여기에 추가
                // 예: 인기 콘서트 캐시 갱신, 시스템 설정 캐시 갱신 등
                
                val elapsed = System.currentTimeMillis() - startTime
                logger.info("🔄 캐시 갱신 완료 ({}ms)", elapsed)
                
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                logger.error("❌ 캐시 갱신 중 오류 발생", e)
            }
        }
    }
    
    /**
     * 통계 리셋 - 매일 자정
     */
    @Scheduled(cron = "0 0 0 * * *")
    fun resetDailyStatistics() {
        try {
            logger.info("📊 일일 통계 리셋 시작")
            
            val finalStats = getSchedulerStatistics()
            logger.info("📈 최종 일일 통계: {}", finalStats)
            
            // 통계 리셋
            reservationCleanupCount.set(0)
            queueProcessCount.set(0)
            tokenCleanupCount.set(0)
            errorCount.set(0)
            
            // 다른 시스템 통계도 리셋
            domainEventPublisher.resetStatistics()
            distributedLock.resetStatistics()
            
            logger.info("✅ 일일 통계 리셋 완료")
            
        } catch (e: Exception) {
            logger.error("❌ 일일 통계 리셋 중 오류 발생", e)
        }
    }
    
    private fun logSchedulerStatistics() {
        val stats = getSchedulerStatistics()
        logger.info("📊 스케줄러 통계: {}", stats)
    }
    
    fun getSchedulerStatistics(): SchedulerStatistics {
        val totalJobs = reservationCleanupCount.get() + queueProcessCount.get() + tokenCleanupCount.get()
        val errors = errorCount.get()
        
        return SchedulerStatistics(
            reservationCleanupCount = reservationCleanupCount.get(),
            queueProcessCount = queueProcessCount.get(),
            tokenCleanupCount = tokenCleanupCount.get(),
            totalJobs = totalJobs,
            errorCount = errors,
            errorRate = if (totalJobs > 0) (errors.toDouble() / totalJobs * 100) else 0.0,
            uptime = getUptimeInfo()
        )
    }
    
    private fun getUptimeInfo(): String {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory
        
        return "메모리: ${usedMemory}MB/${totalMemory}MB"
    }
}

data class SchedulerStatistics(
    val reservationCleanupCount: Int,
    val queueProcessCount: Int,
    val tokenCleanupCount: Int,
    val totalJobs: Int,
    val errorCount: Int,
    val errorRate: Double,
    val uptime: String
)
