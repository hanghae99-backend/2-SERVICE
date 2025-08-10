package kr.hhplus.be.server.api.payment.usecase

import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.lock.LockGuard
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ProcessPaymentUserCase (
    private val paymentService: PaymentService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val balanceService: BalanceService,
    private val deductBalanceUseCase: DeductBalanceUseCase,
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager
){

    private val logger = LoggerFactory.getLogger(ProcessPaymentUserCase::class.java)

    @LockGuard(key = "reservation:#reservationId")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, reservationId: Long, token: String): PaymentDto{
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)

        // 예약 상태로 중복 결제 확인 (예약이 이미 확정되었는지 확인)
        val reservation = reservationService.getReservationWithLock(reservationId)
        logger.info("결제 처리 시작 - userId: $userId, reservationId: $reservationId, status: ${reservation.status.code}")

        if (reservation.userId != userId) {
            throw PaymentProcessException("예약의 사용자가 일치하지 않습니다")
        }
        if (!reservation.isTemporary()) {
            throw PaymentProcessException("임시 예약 상태가 아닙니다: $reservationId, 현재 상태: ${reservation.status.code}")
        }
        if (reservation.isExpired()) {
            throw PaymentProcessException("예약이 만료되었습니다: $reservationId")
        }

        val seatId = reservation.seatId
        val seat = seatService.getSeatById(seatId)
        val paymentAmount = seat.price
        val payment = paymentService.createReservationPayment(userId, reservationId, paymentAmount)

        try {
            val currentBalance = balanceService.getBalance(userId)
            paymentService.validatePaymentAmount(currentBalance.amount, payment.amount)
            deductBalanceUseCase.execute(userId, payment.amount)

            reservationService.confirmReservation(reservationId, payment.paymentId)
            seatService.confirmSeat(seatId)

            val completedPayment = paymentService.completePayment(
                paymentId = payment.paymentId,
                reservationId = reservationId,
                seatId = seatId,
                token = token
            )

            return completedPayment

        } catch (e: OptimisticLockingFailureException) {
            logger.warn("Optimistic lock failure during payment processing. UserId: $userId, ReservationId: $reservationId")
            paymentService.failPayment(
                paymentId = payment.paymentId,
                reservationId = reservationId,
                reason = "동시 결제 요청으로 인한 처리 실패",
                token = token
            )
            throw PaymentProcessException("동일한 예약에 대한 중복 결제 요청입니다. 결제가 취소되었습니다.")
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