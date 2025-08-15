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
    
    // 매 5초마다 대기열 처리 (테스트 환경에서 빠른 처리를 위함)
    @Scheduled(fixedRate = 5000)
    fun processQueue() {
        try {
            tokenLifecycleManager.cleanupExpiredTokens()
            queueManager.processQueueAutomatically()
            val currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            println("대기열 처리 완료 - 시간: $currentTime")
        } catch (e: Exception) {
            println("대기열 자동 처리 중 오류 발생: ${e.message}")
        }
    }
    
    // 매 30초마다 만료된 토큰 정리 (더 빠른 정리를 위함)
    @Scheduled(fixedRate = 30000)
    fun cleanupExpiredTokens() {
        try {
            val cleanedCount = tokenLifecycleManager.cleanupExpiredTokens()
            if (cleanedCount > 0) {
                println("만료된 토큰 ${cleanedCount}개 정리 완료")
            }
        } catch (e: Exception) {
            println("만료된 토큰 정리 중 오류 발생: ${e.message}")
        }
    }
}
