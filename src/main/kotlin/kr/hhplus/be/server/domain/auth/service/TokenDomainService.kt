package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException

import org.springframework.stereotype.Component

/**
 * 순수한 토큰 도메인 로직을 처리하는 POJO 서비스
 * 외부 의존성 없이 토큰 관련 도메인 규칙만 처리
 */
@Component
class TokenDomainService {

    companion object {
        private const val MAX_ACTIVE_TOKENS = 100L
        private const val TOKEN_EXPIRY_MINUTES = 5L
    }

    /**
     * 토큰 활성화 가능 여부 검증
     */
    fun validateTokenActivation(token: WaitingToken, currentStatus: TokenStatus) {
        if (currentStatus != TokenStatus.WAITING) {
            throw TokenActivationException("대기 중인 토큰만 활성화 가능합니다. 현재 상태: $currentStatus")
        }
    }

    /**
     * 활성 토큰 검증
     */
    fun validateActiveToken(token: WaitingToken?, currentStatus: TokenStatus) {
        if (token == null) {
            throw TokenNotFoundException("유효하지 않은 토큰입니다.")
        }
        
        if (currentStatus != TokenStatus.ACTIVE) {
            throw TokenActivationException("활성화된 토큰이 아닙니다. 현재 상태: $currentStatus")
        }
    }

    /**
     * 대기 시간 계산 (분 단위)
     */
    fun calculateWaitingTime(queuePosition: Int): Int {
        // 간단한 계산: 대기 순서 * 2분
        return queuePosition * 2
    }

    /**
     * 예상 대기 시간 계산 (분 단위)
     */
    fun calculateEstimatedWaitingTime(queuePosition: Int): Int? {
        return if (queuePosition >= 0) {
            // 대략적인 계산: 1분에 10명씩 처리된다고 가정
            ((queuePosition + 1) / 10).coerceAtLeast(1)
        } else {
            null
        }
    }

    /**
     * 가용 슬롯 계산
     */
    fun calculateAvailableSlots(currentActiveCount: Long): Int {
        return (MAX_ACTIVE_TOKENS - currentActiveCount).toInt()
    }

    /**
     * 토큰 상태에 따른 메시지 변환
     */
    fun getStatusMessage(status: TokenStatus): String {
        return when (status) {
            TokenStatus.WAITING -> "대기 중입니다"
            TokenStatus.ACTIVE -> "서비스 이용 가능합니다"
            TokenStatus.EXPIRED -> "토큰이 만료되었습니다"
        }
    }

    /**
     * 대기열 상태에 따른 메시지 변환
     */
    fun getQueueStatusMessage(status: TokenStatus, queuePosition: Int): String {
        return when (status) {
            TokenStatus.WAITING -> "대기 중입니다"
            TokenStatus.ACTIVE -> "서비스 이용 가능합니다"
            TokenStatus.EXPIRED -> "토큰이 만료되었습니다"
        }
    }
}
