package kr.hhplus.be.server.api.payment.usecase

import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ProcessPaymentUseCase(
    private val paymentService: PaymentService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val deductBalanceUseCase: DeductBalanceUseCase,
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val concertScheduleRepository: ConcertScheduleRepository
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(ProcessPaymentUseCase::class.java)
    }

    @LockGuard(
        keys = ["'balance:' + #userId", "'reservation:' + #reservationId", "'seat:' + #seatId", "'payment:' + #userId + ':' + #reservationId"],
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 20000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, reservationId: Long, seatId: Long, token: String): PaymentDto {
        logger.info("결제 처리 시작 - userId: {}, reservationId: {}, seatId: {}", userId, reservationId, seatId)
        
        validateToken(token)
        validateReservation(reservationId, userId)
        
        val seat = seatService.getSeatById(seatId)
        val schedule = concertScheduleRepository.findById(seat.scheduleId)
            ?: throw IllegalStateException("스케줄 정보를 찾을 수 없습니다: ${seat.scheduleId}")
        val payment = paymentService.createReservationPayment(userId, reservationId, seat.price)
        
        return try {
            // 1. 밸런스 차감
            deductBalanceUseCase.executeInternal(userId, payment.amount)
            
            // 2. 결제 완료 처리
            val completedPayment = paymentService.completePayment(
                paymentId = payment.paymentId,
                reservationId = reservationId,
                seatId = seatId,
                token = token,
                scheduleId = seat.scheduleId,
                seatNumber = seat.seatNumber,
                concertId = schedule.concertId
            )
            
            // 3. 예약 확정 처리 (동기적)
            reservationService.confirmReservation(reservationId, payment.paymentId)
            
            // 4. 좌석 확정 처리 (동기적)
            seatService.confirmSeat(seatId)
            
            // 5. 토큰 완료 처리 (동기적)
            tokenLifecycleManager.completeToken(token)
            

            logger.info("결제 처리 완료 - userId: {}, paymentId: {}, reservationId: {}, seatId: {}", 
                userId, payment.paymentId, reservationId, seatId)
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
        
        logger.info("예약 상태 검증 - reservationId: {}, status: {}", reservationId, reservation.status.code)
        
        if (reservation.userId != userId) {
            throw PaymentProcessException("예약의 사용자가 일치하지 않습니다")
        }
        
        if (reservation.status.code != ReservationStatusType.TEMPORARY) {
            throw PaymentProcessException("임시 예약 상태가 아닙니다: $reservationId, 현재 상태: ${reservation.status.code}")
        }
        
        if (reservation.isExpired()) {
            throw PaymentProcessException("예약이 만료되었습니다: $reservationId")
        }
    }
    
    private fun handlePaymentFailure(
        paymentId: Long,
        reservationId: Long, 
        token: String,
        exception: Exception,
        userId: Long
    ) {
        logger.error("결제 처리 실패 - userId: {}, reservationId: {}, paymentId: {}", userId, reservationId, paymentId, exception)
        
        // 결제 실패 처리 (동기적)
        paymentService.failPayment(
            paymentId = paymentId,
            reservationId = reservationId,
            reason = exception.message ?: "Unknown error",
            token = token
        )
        
        // 토큰 완료 처리 (동기적)
        try {
            tokenLifecycleManager.completeToken(token)
            logger.info("결제 실패 시 토큰 완료 처리 - token: {}", token)
        } catch (e: Exception) {
            logger.error("토큰 완료 처리 실패 - token: {}", token, e)
        }
    }
}
