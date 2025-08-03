package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import org.springframework.stereotype.Component


@Component
class TokenLifecycleManager(
    private val tokenStore: TokenStore,
    private val queueManager: QueueManager
) {
    

    fun saveToken(waitingToken: WaitingToken) {
        tokenStore.save(waitingToken)
    }
    

    fun getTokenStatus(token: String): TokenStatus {
        return tokenStore.getTokenStatus(token)
    }
    

    fun findToken(token: String): WaitingToken? {
        return tokenStore.findByToken(token)
    }
    

    fun expireToken(token: String) {
        tokenStore.expireToken(token)
    }
    

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
    
    // 예약/결제 완료 시 토큰 만료 및 다음 사용자 활성화
    fun completeToken(token: String) {
        // 토큰 만료
        expireToken(token)
        
        // 즉시 다음 사용자 활성화
        queueManager.processQueueAutomatically()
    }
}
