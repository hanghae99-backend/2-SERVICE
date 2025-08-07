package kr.hhplus.be.server.global.scheduler

import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReservationScheduler(
    private val reservationService: ReservationService,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val queueManager: QueueManager
) {
    
    // 매 1분마다 만료된 예약 정리
    @Scheduled(fixedRate = 60000)
    fun cleanupExpiredReservations() {
        try {
            val cleanedCount = reservationService.cleanupExpiredReservations()
            if (cleanedCount > 0) {
                println("만료된 예약 $cleanedCount 건을 정리했습니다.")
            }
        } catch (e: Exception) {
            println("만료된 예약 정리 중 오류 발생: ${e.message}")
        }
    }
    
    // 매 30초마다 대기열 처리 (단순한 로직 - service 직접 호출)
    @Scheduled(fixedRate = 30000)
    fun processQueue() {
        try {
            tokenLifecycleManager.cleanupExpiredTokens()
            queueManager.processQueueAutomatically()
        } catch (e: Exception) {
            println("대기열 자동 처리 중 오류 발생: ${e.message}")
        }
    }
    
    // 매 5분마다 만료된 토큰 정리 (단순한 로직 - service 직접 호출)
    @Scheduled(fixedRate = 300000)
    fun cleanupExpiredTokens() {
        try {
            tokenLifecycleManager.cleanupExpiredTokens()
        } catch (e: Exception) {
            println("만료된 토큰 정리 중 오류 발생: ${e.message}")
        }
    }
}
