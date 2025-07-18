package kr.hhplus.be.server.payment.service

import kr.hhplus.be.server.global.exception.ParameterValidationException
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 결제 파라미터 검증의 단일 책임을 가진다
 * 입력값 형식, 범위 등 기본적인 검증을 담당
 */
@Component
class PaymentParameterValidator {
    
    /**
     * 사용자 ID 파라미터 검증
     */
    fun validateUserId(userId: Long) {
        if (userId <= 0) {
            throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
        }
    }
    
    /**
     * 예약 ID 파라미터 검증
     */
    fun validateReservationId(reservationId: Long) {
        if (reservationId <= 0) {
            throw ParameterValidationException("예약 ID는 0보다 커야 합니다: $reservationId")
        }
    }
    
    /**
     * 토큰 파라미터 검증
     */
    fun validateToken(token: String) {
        if (token.isBlank()) {
            throw ParameterValidationException("토큰은 비어있을 수 없습니다")
        }
        if (token.length < 10) {
            throw ParameterValidationException("토큰 길이가 너무 짧습니다: ${token.length}")
        }
    }
    
    /**
     * 결제 ID 파라미터 검증
     */
    fun validatePaymentId(paymentId: Long) {
        if (paymentId <= 0) {
            throw ParameterValidationException("결제 ID는 0보다 커야 합니다: $paymentId")
        }
    }
}
