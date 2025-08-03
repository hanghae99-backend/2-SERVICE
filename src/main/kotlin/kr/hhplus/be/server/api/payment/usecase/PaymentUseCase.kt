package kr.hhplus.be.server.api.payment.usecase

import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.api.auth.usecase.TokenUseCase
import kr.hhplus.be.server.api.balance.usecase.BalanceUseCase
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockKeyManager
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
@Transactional(readOnly = true)
class PaymentUseCase(
    private val paymentService: PaymentService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val userService: UserService,
    private val balanceUseCase: BalanceUseCase,
    private val tokenUseCase: TokenUseCase,
    private val distributedLock: DistributedLock
) {

    @Transactional
    fun processPayment(userId: Long, reservationId: Long, token: String): PaymentDto {
        val lockKey = LockKeyManager.paymentProcess(userId, reservationId)
        
        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 15000L,  // 결제는 조금 더 긴 시간 허용
            waitTimeoutMs = 10000L
        ) {
            processPaymentInternal(userId, reservationId, token)
        }
    }
    
    private fun processPaymentInternal(userId: Long, reservationId: Long, token: String): PaymentDto {

        tokenUseCase.validateActiveToken(token)


        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }


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

            val currentBalance = balanceUseCase.getBalance(userId)
            paymentService.validatePaymentAmount(currentBalance.amount, payment.amount)
            balanceUseCase.deductBalanceInternal(userId, payment.amount)

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

    fun getPaymentById(paymentId: Long): PaymentDto {
        return paymentService.getPaymentById(paymentId)
    }
}
