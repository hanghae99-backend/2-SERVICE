package kr.hhplus.be.server.api.payment.usecase

import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.springframework.stereotype.Service

@Service
class ProcessPaymentUserCase (
    private val paymentService: PaymentService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val balanceService: BalanceService,
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager
){

    @ValidateUserId
    fun execute(userId: Long, reservationId: Long, token: String): PaymentDto{
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)

        val reservation = reservationService.getReservationById(reservationId)

        if (reservation.userId != userId) {
            throw PaymentProcessException("예약의 사용자가 일치하지 않습니다")
        }
        if (!reservation.isTemporary()) {
            throw PaymentProcessException("임시 예약 상태가 아닙니다: $reservationId")
        }
        if (reservation.isExpired()) {
            throw PaymentProcessException("예약이 만료되었습니다: $reservationId")
        }

        val seatId = reservation.seatId
        val seat = seatService.getSeatById(seatId)
        val paymentAmount = seat.price
        val payment = paymentService.createPayment(userId, paymentAmount)

        try {
            val currentBalance = balanceService.getBalance(userId)
            paymentService.validatePaymentAmount(currentBalance.amount, payment.amount)
            balanceService.deductBalance(userId, payment.amount)

            reservationService.confirmReservationInternal(reservationId, payment.paymentId)
            seatService.confirmSeatInternal(seatId)

            val completedPayment = paymentService.completePayment(
                paymentId = payment.paymentId,
                reservationId = reservationId,
                seatId = seatId,
                token = token
            )

            return completedPayment

        } catch (e: Exception) {
            paymentService.failPayment(
                paymentId = payment.paymentId,
                reservationId = reservationId,
                reason = e.message ?: "Unknown error",
                token = token
            )

            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }
}