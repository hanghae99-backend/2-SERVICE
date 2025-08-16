package kr.hhplus.be.server.api.payment.usecase

import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ProcessPaymentUserCase(
    private val paymentService: PaymentService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val deductBalanceUseCase: DeductBalanceUseCase,
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager
) {
    
    private val logger = LoggerFactory.getLogger(ProcessPaymentUserCase::class.java)

    @LockGuard(
        keys = ["'balance:' + #userId", "'reservation:' + #reservationId"],
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 20000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, reservationId: Long, seatId: Long, token: String): PaymentDto {
        logger.info("결제 처리 시작 - userId: $userId, reservationId: $reservationId, seatId: $seatId")
        
        validateToken(token)
        val reservation = validateReservation(reservationId, userId)
        
        val seat = seatService.getSeatById(seatId)
        val payment = paymentService.createReservationPayment(userId, reservationId, seat.price)
        
        return try {
            deductBalanceUseCase.execute(userId, payment.amount)
            
            reservationService.confirmReservation(reservationId, payment.paymentId)
            seatService.confirmSeat(seatId)
            
            val completedPayment = paymentService.completePayment(
                paymentId = payment.paymentId,
                reservationId = reservationId,
                seatId = seatId,
                token = token
            )
            
            logger.info("결제 처리 완료 - userId: $userId, paymentId: ${payment.paymentId}")
            completedPayment
            
        } catch (e: Exception) {
            handlePaymentFailure(payment.paymentId, reservationId, token, e, userId)
            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}", e)
        }
    }
    
    private fun validateToken(token: String) {
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)
    }
    
    private fun validateReservation(reservationId: Long, userId: Long) {
        val reservation = reservationService.getReservationById(reservationId)
        
        logger.info("예약 상태 검증 - reservationId: $reservationId, status: ${reservation.status.code}")
        
        if (reservation.userId != userId) {
            throw PaymentProcessException("예약의 사용자가 일치하지 않습니다")
        }
        
        if (!reservation.isTemporary()) {
            throw PaymentProcessException("임시 예약 상태가 아닙니다: $reservationId, 현재 상태: ${reservation.status.code}")
        }
        
        if (reservation.isExpired()) {
            throw PaymentProcessException("예약이 만료되었습니다: $reservationId")
        }
        
        return reservation
    }
    
    private fun handlePaymentFailure(
        paymentId: Long,
        reservationId: Long, 
        token: String,
        exception: Exception,
        userId: Long
    ) {
        logger.error("결제 처리 실패 - userId: $userId, reservationId: $reservationId, paymentId: $paymentId", exception)
        
        try {
            paymentService.failPayment(
                paymentId = paymentId,
                reservationId = reservationId,
                reason = exception.message ?: "Unknown error",
                token = token
            )
        } catch (failException: Exception) {
            logger.error("결제 실패 처리 중 오류 발생 - paymentId: $paymentId", failException)
        }
    }
}
