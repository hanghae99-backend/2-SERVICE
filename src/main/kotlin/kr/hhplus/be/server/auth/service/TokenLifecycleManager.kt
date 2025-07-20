package kr.hhplus.be.server.auth.service

import kr.hhplus.be.server.auth.entity.TokenStatus
import kr.hhplus.be.server.auth.entity.WaitingToken
import kr.hhplus.be.server.auth.repository.TokenStore
import org.springframework.stereotype.Component

/**
 * 토큰 생명주기 관리의 단일 책임을 가진다
 */
@Component
class TokenLifecycleManager(
    private val tokenStore: TokenStore,
    private val queueManager: QueueManager
) {
    
    /**
     * 토큰 저장
     */
    fun saveToken(waitingToken: WaitingToken) {
        tokenStore.save(waitingToken)
    }
    
    /**
     * 토큰 상태 조회
     */
    fun getTokenStatus(token: String): TokenStatus {
        return tokenStore.getTokenStatus(token)
    }
    
    /**
     * 토큰 조회
     */
    fun findToken(token: String): WaitingToken? {
        return tokenStore.findByToken(token)
    }
    
    /**
     * 토큰 만료
     */
    fun expireToken(token: String) {
        tokenStore.expireToken(token)
    }
    
    /**
     * 만료된 토큰들 정리
     */
    fun cleanupExpiredTokens() {
        val expiredTokens = tokenStore.findExpiredActiveTokens()
        expiredTokens.forEach { expiredToken ->
            try {
                expireToken(expiredToken)
                println("만료된 활성 토큰 정리: $expiredToken")
            } catch (e: Exception) {
                println("토큰 만료 처리 실패: $expiredToken, 오류: ${e.message}")
            }
        }
    }
    
    /**
     * 토큰 완료 처리 (예약/결제 완료 시)
     */
    fun completeToken(token: String) {
        // 토큰 만료
        expireToken(token)
        
        // 즉시 다음 사용자 활성화
        queueManager.processQueueAutomatically()
    }
}
