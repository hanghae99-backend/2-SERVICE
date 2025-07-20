package kr.hhplus.be.server.global.scheduler

import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.payment.service.ReservationService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReservationScheduler(
    private val reservationService: ReservationService,
    private val tokenService: TokenService
) {
    
    /**
     * 매 1분마다 만료된 예약들을 정리
     */
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    fun cleanupExpiredReservations() {
        try {
            reservationService.cancelExpiredReservations()
        } catch (e: Exception) {
            println("만료된 예약 정리 중 오류 발생: ${e.message}")
        }
    }
    
    /**
     * 매 30초마다 대기열 자동 처리
     */
    @Scheduled(fixedRate = 30000) // 30초마다 실행
    fun processQueue() {
        try {
            tokenService.processQueueAutomatically()
        } catch (e: Exception) {
            println("대기열 자동 처리 중 오류 발생: ${e.message}")
        }
    }
    
    /**
     * 매 5분마다 만료된 토큰 정리
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    fun cleanupExpiredTokens() {
        try {
            tokenService.cleanupExpiredActiveTokens()
        } catch (e: Exception) {
            println("만료된 토큰 정리 중 오류 발생: ${e.message}")
        }
    }
}
