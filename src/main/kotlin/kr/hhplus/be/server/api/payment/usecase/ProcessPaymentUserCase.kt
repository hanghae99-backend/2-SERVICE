package kr.hhplus.be.server.api.payment.usecase

import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
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
class ProcessPaymentUserCase (
    private val paymentService: PaymentService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val balanceService: BalanceService,
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager
){

    private val logger = LoggerFactory.getLogger(ProcessPaymentUserCase::class.java)

    @LockGuard(
        keys = ["'balance:' + #userId", "'reservation:' + #reservationId", "'seat:' + #seatId"],
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 15000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, reservationId: Long, seatId: Long, token: String): PaymentDto{
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)

        // 예약 상태로 중복 결제 확인 (예약이 이미 확정되었는지 확인)
        val reservation = reservationService.getReservationById(reservationId)
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
        
        // 결제 요청의 seatId와 예약의 seatId가 일치하는지 검증
        if (reservation.seatId != seatId) {
            throw PaymentProcessException("예약의 좌석과 결제 요청의 좌석이 일치하지 않습니다")
        }
        val seat = seatService.getSeatById(seatId)
        val paymentAmount = seat.price
        val payment = paymentService.createReservationPayment(userId, reservationId, paymentAmount)

        try {
            val currentBalance = balanceService.getBalance(userId)
            paymentService.validatePaymentAmount(currentBalance.amount, payment.amount)
            
            val currentPoint = pointRepository.findByUserId(userId)
                ?: throw PointNotFoundException("포인트 정보를 찾을 수 없습니다")
            val deductedPoint = currentPoint.deduct(payment.amount)
            pointRepository.save(deductedPoint)
            val useType = pointHistoryTypeRepository.getUseType()
            val history = PointHistory.use(userId, payment.amount, useType, "포인트 사용")
            pointHistoryRepository.save(history)

            reservationService.confirmReservation(reservationId, payment.paymentId)
            seatService.confirmSeat(seatId)

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