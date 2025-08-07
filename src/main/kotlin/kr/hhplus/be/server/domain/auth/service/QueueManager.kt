package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import org.springframework.stereotype.Component


@Component
class QueueManager(
    private val tokenStore: TokenStore
) {
    
    companion object {
        private const val MAX_ACTIVE_TOKENS = 100L
    }


    fun addToQueue(token: String) {
        tokenStore.addToWaitingQueue(token)
    }


    fun calculateAvailableSlots(): Int {
        val currentActiveCount = tokenStore.countActiveTokens()
        return (MAX_ACTIVE_TOKENS - currentActiveCount).toInt()
    }


    fun getNextTokensFromQueue(count: Int): List<String> {
        return if (count > 0) {
            tokenStore.getNextTokensFromQueue(count)
        } else {
            emptyList()
        }
    }


    fun activateToken(token: String) {
        tokenStore.activateToken(token)
    }

    // 가용 슬롯만큼 대기열에서 토큰 활성화
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


    fun getQueueStatus(token: String): TokenQueueDetail {
        val tokenStatus = tokenStore.getTokenStatus(token)
        val queuePosition = tokenStore.getQueuePosition(token)
        val isActive = tokenStatus.name == "ACTIVE"
        val status = tokenStatus.name
        val message = when (tokenStatus.name) {
            "ACTIVE" -> "토큰이 활성화되었습니다"
            "WAITING" -> "대기 중입니다"
            "EXPIRED" -> "토큰이 만료되었습니다"
            else -> "알 수 없는 상태입니다"
        }
        val estimatedWaitingTime = if (isActive) null else calculateEstimatedWaitingTime(queuePosition)

        return TokenQueueDetail.fromTokenWithQueue(
            token = token,
            status = status,
            message = message,
            queuePosition = if (isActive) null else queuePosition,
            estimatedWaitingTime = estimatedWaitingTime
        )
    }

    // 대기열 예상 대기 시간 계산
    private fun calculateEstimatedWaitingTime(queuePosition: Int): Int? {
        return if (queuePosition >= 0) {
            // 1분에 10명씩 처리 가정
            ((queuePosition + 1) / 10).coerceAtLeast(1)
        } else {
            null
        }
    }


    fun getQueuePosition(token: String): Int {
        return tokenStore.getQueuePosition(token)
    }
}
