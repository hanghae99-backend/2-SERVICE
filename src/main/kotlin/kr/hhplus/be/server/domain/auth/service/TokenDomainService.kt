package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.global.properties.ConcertProperties
import org.springframework.stereotype.Component


@Component
class TokenDomainService(
    private val concertProperties: ConcertProperties
) {
    fun validateTokenActivation(token: WaitingToken, currentStatus: TokenStatus) {
        if (currentStatus != TokenStatus.WAITING) {
            throw TokenActivationException("대기 중인 토큰만 활성화 가능합니다. 현재 상태: $currentStatus")
        }
    }

    fun validateActiveToken(token: WaitingToken?, currentStatus: TokenStatus) {
        if (token == null) {
            throw TokenNotFoundException("유효하지 않은 토큰입니다.")
        }
        if (currentStatus != TokenStatus.ACTIVE) {
            throw TokenActivationException("활성화된 토큰이 아닙니다. 현재 상태: $currentStatus")
        }
    }

    fun calculateWaitingTime(queuePosition: Int): Int {
        return queuePosition * concertProperties.queue.waitingTimeMultiplier
    }

    fun calculateEstimatedWaitingTime(queuePosition: Int): Int? {
        return if (queuePosition >= 0) {
            ((queuePosition + 1) / concertProperties.queue.tokensPerMinute).coerceAtLeast(1)
        } else {
            null
        }
    }

    fun calculateAvailableSlots(currentActiveCount: Long): Int {
        return (concertProperties.queue.maxActiveTokens - currentActiveCount).toInt()
    }


    fun getStatusMessage(status: TokenStatus): String {
        return when (status) {
            TokenStatus.WAITING -> "대기 중입니다"
            TokenStatus.ACTIVE -> "서비스 이용 가능합니다"
            TokenStatus.EXPIRED -> "토큰이 만료되었습니다"
            TokenStatus.USED -> "토큰이 사용되었습니다"
        }
    }


    fun getQueueStatusMessage(status: TokenStatus, queuePosition: Int): String {
        return when (status) {
            TokenStatus.WAITING -> "대기 중입니다"
            TokenStatus.ACTIVE -> "서비스 이용 가능합니다"
            TokenStatus.EXPIRED -> "토큰이 만료되었습니다"
            TokenStatus.USED -> "토큰이 사용되었습니다"
        }
    }
}
