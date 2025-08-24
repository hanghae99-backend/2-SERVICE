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
    
    fun findActiveTokenByUserId(userId: Long): WaitingToken? {
        return tokenStore.findActiveTokenByUserId(userId)
    }
    

    fun expireToken(token: String) {
        tokenStore.expireToken(token)
    }
    

    // 만료된 토큰만 정리 (활성화는 별도 스케줄러에서 처리)
    fun cleanupExpiredTokens(): Int {
        val expiredTokens = tokenStore.findExpiredActiveTokens()
        var cleanedCount = 0
        
        expiredTokens.forEach { expiredToken ->
            try {
                expireToken(expiredToken)
                cleanedCount++
                println("만료된 활성 토큰 정리: $expiredToken")
            } catch (e: Exception) {
                println("토큰 만료 처리 실패: $expiredToken, 오류: ${e.message}")
            }
        }
        
        return cleanedCount
    }

    @Deprecated("스케줄러 분리로 인해 사용 중단 예정")
    fun cleanupExpiredTokensAndProcessQueue(): Pair<Int, Int> {
        val expiredTokens = tokenStore.findExpiredActiveTokens()
        var cleanedCount = 0
        
        // 1. 만료된 토큰 정리
        expiredTokens.forEach { expiredToken ->
            try {
                expireToken(expiredToken)
                cleanedCount++
                println("만료된 활성 토큰 정리: $expiredToken")
            } catch (e: Exception) {
                println("토큰 만료 처리 실패: $expiredToken, 오류: ${e.message}")
            }
        }
        
        // 2. 만료된 토큰 수만큼 대기열에서 새로운 토큰 활성화
        val activatedCount = if (cleanedCount > 0) {
            queueManager.processQueueAutomatically()
        } else {
            0
        }
        
        return Pair(cleanedCount, activatedCount)
    }
    
    // 예약/결제 완료 시 토큰만 만료 (활성화는 스케줄러에서 처리)
    fun completeToken(token: String) {
        // 토큰만 만료 처리
        expireToken(token)
        println("예약/결제 완료로 토큰 만료: $token")
    }
}
