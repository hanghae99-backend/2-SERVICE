package kr.hhplus.be.server.payment.service

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
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다: $userId" }
    }
    
    /**
     * 예약 ID 파라미터 검증
     */
    fun validateReservationId(reservationId: Long) {
        require(reservationId > 0) { "예약 ID는 0보다 커야 합니다: $reservationId" }
    }
    
    /**
     * 토큰 파라미터 검증
     */
    fun validateToken(token: String) {
        require(token.isNotBlank()) { "토큰은 비어있을 수 없습니다" }
        require(token.length >= 10) { "토큰 길이가 너무 짧습니다" }
    }
    
    /**
     * 결제 ID 파라미터 검증
     */
    fun validatePaymentId(paymentId: Long) {
        require(paymentId > 0) { "결제 ID는 0보다 커야 합니다: $paymentId" }
    }
    
    /**
     * 금액 파라미터 검증
     */
    fun validateAmount(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "금액은 0보다 커야 합니다: $amount" }
        require(amount <= BigDecimal("1000000")) { "금액이 너무 큽니다: $amount" }
    }
}
