package kr.hhplus.be.server.auth.infrastructure.scheduler

import kr.hhplus.be.server.auth.service.TokenService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class QueueScheduler(
    private val tokenService: TokenService
) {
    
    @Scheduled(fixedDelay = 3000) // 3초마다 실행 - 빠른 반응성
    fun processWaitingQueue() {
        try {
            tokenService.processQueueAutomatically()
        } catch (e: Exception) {
            // 로그 처리
            println("큐 처리 중 오류 발생: ${e.message}")
        }
    }
    
    @Scheduled(fixedDelay = 30000) // 30초마다 실행 - 만료 토큰 정리
    fun cleanupExpiredTokens() {
        try {
            tokenService.cleanupExpiredActiveTokens()
        } catch (e: Exception) {
            println("만료된 토큰 정리 중 오류 발생: ${e.message}")
        }
    }
    
    @Scheduled(fixedDelay = 60000) // 1분마다 실행 - 시스템 상태 로깅
    fun logQueueStatus() {
        try {
            val status = tokenService.getQueueStatus()
            println("큐 상태 - 대기: ${status.queueSize}, 활성: ${status.activeTokens}, 가용: ${status.availableSlots}")
        } catch (e: Exception) {
            println("큐 상태 로깅 중 오류 발생: ${e.message}")
        }
    }
}
