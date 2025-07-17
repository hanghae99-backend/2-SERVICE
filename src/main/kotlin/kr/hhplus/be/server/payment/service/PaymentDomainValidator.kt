package kr.hhplus.be.server.payment.service

import kr.hhplus.be.server.balance.entity.InsufficientBalanceException
import kr.hhplus.be.server.payment.entity.*
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 결제 도메인 검증의 단일 책임을 가진다
 * 결제 관련 비즈니스 규칙과 도메인 상태에 대한 검증을 담당
 */
@Component
class PaymentDomainValidator {
    
    /**
     * 예약 유효성 검증
     * 비즈니스 규칙: 예약자와 결제자가 일치해야 함
     */
    fun validateReservationOwnership(reservation: Reservation, userId: Long) {
        if (reservation.userId != userId) {
            throw IllegalArgumentException("예약의 사용자가 일치하지 않습니다")
        }
    }
    
    /**
     * 예약 상태 검증
     * 비즈니스 규칙: 임시 예약 상태여야 결제 가능
     */
    fun validateReservationStatus(reservation: Reservation) {
        if (!reservation.isTemporary()) {
            throw InvalidReservationStatusException("임시 예약 상태가 아닙니다: ${reservation.reservationId}")
        }
    }
    
    /**
     * 예약 만료 검증
     * 비즈니스 규칙: 만료된 예약은 결제 불가
     */
    fun validateReservationNotExpired(reservation: Reservation) {
        if (reservation.isExpired()) {
            throw ReservationExpiredException("예약이 만료되었습니다: ${reservation.reservationId}")
        }
    }
    
    /**
     * 중복 결제 방지 검증
     * 비즈니스 규칙: 하나의 예약에 대해 하나의 결제만 허용
     */
    fun validateNoDuplicatePayment(reservationId: Long, paymentExists: Boolean) {
        if (paymentExists) {
            throw PaymentAlreadyProcessedException("이미 결제가 진행된 예약입니다: $reservationId")
        }
    }
    
    /**
     * 잔액 충분성 검증
     * 비즈니스 규칙: 결제 금액만큼 잔액이 있어야 함
     */
    fun validateSufficientBalance(hasEnoughBalance: Boolean) {
        if (!hasEnoughBalance) {
            throw InsufficientBalanceException("잔액이 부족합니다")
        }
    }
    
    /**
     * 결제 금액 유효성 검증
     * 비즈니스 규칙: 결제 금액은 양수여야 함
     */
    fun validatePaymentAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("결제 금액은 0보다 커야 합니다: $amount")
        }
    }
}
