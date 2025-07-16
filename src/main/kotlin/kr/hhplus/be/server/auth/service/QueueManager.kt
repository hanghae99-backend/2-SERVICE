package kr.hhplus.be.server.auth.service

import kr.hhplus.be.server.auth.repository.TokenStore
import org.springframework.stereotype.Component

/**
 * 대기열 관리의 단일 책임을 가진다
 */
@Component
class QueueManager(
    private val tokenStore: TokenStore
) {
    
    companion object {
        private const val MAX_ACTIVE_TOKENS = 100L
    }

    /**
     * 토큰을 대기열에 추가
     */
    fun addToQueue(token: String) {
        tokenStore.addToWaitingQueue(token)
    }

    /**
     * 가용 슬롯 계산
     */
    fun calculateAvailableSlots(): Int {
        val currentActiveCount = tokenStore.countActiveTokens()
        return (MAX_ACTIVE_TOKENS - currentActiveCount).toInt()
    }

    /**
     * 대기열에서 다음 토큰들 가져오기
     */
    fun getNextTokensFromQueue(count: Int): List<String> {
        return if (count > 0) {
            tokenStore.getNextTokensFromQueue(count)
        } else {
            emptyList()
        }
    }

    /**
     * 토큰을 활성화
     */
    fun activateToken(token: String) {
        tokenStore.activateToken(token)
    }

    /**
     * 자동으로 대기열 처리 (가용 슬롯만큼 활성화)
     */
    fun processQueueAutomatically() {
        val availableSlots = calculateAvailableSlots()
        if (availableSlots > 0) {
            val tokensToActivate = getNextTokensFromQueue(availableSlots)
            tokensToActivate.forEach { tokenString ->
                try {
                    activateToken(tokenString)
                    println("토큰 자동 활성화: $tokenString")
                } catch (e: Exception) {
                    println("토큰 활성화 실패: $tokenString, 오류: ${e.message}")
                }
            }
        }
    }

    /**
     * 큐 상태 조회
     */
    fun getQueueStatus(): QueueStatusResponse {
        val queueSize = tokenStore.getQueueSize()
        val activeCount = tokenStore.countActiveTokens()
        val availableSlots = MAX_ACTIVE_TOKENS - activeCount

        return QueueStatusResponse(
            queueSize = queueSize,
            activeTokens = activeCount,
            maxActiveTokens = MAX_ACTIVE_TOKENS,
            availableSlots = availableSlots
        )
    }

    /**
     * 대기 순서 조회
     */
    fun getQueuePosition(token: String): Int {
        return tokenStore.getQueuePosition(token)
    }
}
