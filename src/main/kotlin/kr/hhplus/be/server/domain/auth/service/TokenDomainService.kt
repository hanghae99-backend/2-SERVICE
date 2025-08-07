package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException

import org.springframework.stereotype.Component


@Component
class TokenDomainService {

    companion object {
        private const val MAX_ACTIVE_TOKENS = 100L
        private const val TOKEN_EXPIRY_MINUTES = 5L
    }


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

    // 대기 시간 계산: 대기 순서 * 2분
    fun calculateWaitingTime(queuePosition: Int): Int {
        // 간단한 계산: 대기 순서 * 2분
        return queuePosition * 2
    }

    // 대기열 예상 대기 시간 계산
    fun calculateEstimatedWaitingTime(queuePosition: Int): Int? {
        return if (queuePosition >= 0) {
            // 1분에 10명씩 처리 가정
            ((queuePosition + 1) / 10).coerceAtLeast(1)
        } else {
            null
        }
    }


    fun calculateAvailableSlots(currentActiveCount: Long): Int {
        return (MAX_ACTIVE_TOKENS - currentActiveCount).toInt()
    }


    fun getStatusMessage(status: TokenStatus): String {
        return when (status) {
            TokenStatus.WAITING -> "대기 중입니다"
            TokenStatus.ACTIVE -> "서비스 이용 가능합니다"
            TokenStatus.EXPIRED -> "토큰이 만료되었습니다"
        }
    }


    fun getQueueStatusMessage(status: TokenStatus, queuePosition: Int): String {
        return when (status) {
            TokenStatus.WAITING -> "대기 중입니다"
            TokenStatus.ACTIVE -> "서비스 이용 가능합니다"
            TokenStatus.EXPIRED -> "토큰이 만료되었습니다"
        }
    }
}
