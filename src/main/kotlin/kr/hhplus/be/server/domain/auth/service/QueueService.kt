package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy
import org.springframework.stereotype.Component

@Component
class QueueService(
    private val tokenStore: TokenStore
) {
    
    companion object {
        private const val MAX_ACTIVE_TOKENS = 100L
    }
    
    fun addToQueue(token: String) {
        tokenStore.addToWaitingQueue(token)
    }
    
    fun getQueuePosition(token: String): Int {
        return tokenStore.getQueuePosition(token)
    }
    
    fun calculateWaitingTime(queuePosition: Int): Int {
        return queuePosition * 2
    }
    
    fun getTokenQueueInfo(token: String, userId: Long): TokenIssueDetail {
        val queuePosition = getQueuePosition(token) + 1
        val estimatedWaitingTime = calculateWaitingTime(queuePosition)
        
        return TokenIssueDetail.fromTokenWithDetails(
            token = token,
            status = "WAITING",
            message = "이미 대기열에 등록된 토큰입니다",
            userId = userId,
            queuePosition = queuePosition,
            estimatedWaitingTime = estimatedWaitingTime
        )
    }
    
    fun getTokenStatusDetail(token: String): TokenQueueDetail {
        val status = tokenStore.getTokenStatus(token)
        val (message, queuePosition, estimatedTime) = when (status) {
            TokenStatus.WAITING -> {
                val position = getQueuePosition(token)
                val pos = if (position >= 0) position + 1 else null
                val time = pos?.let { calculateWaitingTime(it) }
                Triple("대기 중입니다", pos, time)
            }
            TokenStatus.ACTIVE -> Triple("서비스 이용 가능합니다", null, null)
            TokenStatus.EXPIRED -> Triple("토큰이 만료되었습니다", null, null)
            TokenStatus.USED -> Triple("토큰이 사용되었습니다", null, null)
        }
        
        return TokenQueueDetail.fromTokenWithQueue(
            token = token,
            status = status.name,
            message = message,
            queuePosition = queuePosition,
            estimatedWaitingTime = estimatedTime
        )
    }
    
    fun calculateAvailableSlots(): Int {
        val currentActiveCount = tokenStore.countActiveTokens()
        return (MAX_ACTIVE_TOKENS - currentActiveCount).toInt()
    }
    
    @LockGuard(key = "'queue:process'", strategy = LockStrategy.SPIN, waitTimeoutMs = 5000L)
    fun processNext() {
        val availableSlots = calculateAvailableSlots()
        if (availableSlots > 0) {
            val nextTokens = tokenStore.getNextTokensFromQueue(availableSlots)
            nextTokens.forEach { tokenStore.activateToken(it) }
        }
    }
}