package kr.hhplus.be.server.payment.service

import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.balance.service.BalanceService
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockKeyManager
import kr.hhplus.be.server.payment.dto.PaymentDto
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.payment.exception.PaymentProcessException
import kr.hhplus.be.server.payment.repository.PaymentRepository
import kr.hhplus.be.server.payment.repository.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.reservation.service.ReservationService
import kr.hhplus.be.server.user.service.UserService
import kr.hhplus.be.server.user.exception.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentStatusTypeRepository: PaymentStatusTypePojoRepository,
    private val reservationService: ReservationService,
    private val balanceService: BalanceService,
    private val tokenService: TokenService,
    private val seatService: SeatService,
    private val userService: UserService,
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
        // 1. 토큰 검증
        tokenService.validateActiveToken(token)

        // 2. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }

        // 3. 예약 정보 조회 및 검증 (ReservationService 통해)
        val reservation = reservationService.getReservationById(reservationId)
        
        // 4. 예약 유효성 검증
        if (reservation.userId != userId) {
            throw PaymentProcessException("예약의 사용자가 일치하지 않습니다")
        }
        if (!reservation.isTemporary()) {
            throw PaymentProcessException("임시 예약 상태가 아닙니다: $reservationId")
        }
        if (reservation.isExpired()) {
            throw PaymentProcessException("예약이 만료되었습니다: $reservationId")
        }

        // 5. 좌석 정보 조회
        val seatId = reservation.seatId
        val seat = seatService.getSeatById(seatId)
        val paymentAmount = seat.price

        // 6. 잔액 확인 및 차감
        val currentBalance = balanceService.getBalance(userId)
        if (currentBalance.amount < paymentAmount) {
            throw PaymentProcessException("잔액이 부족합니다. 현재 잔액: ${currentBalance.amount}, 필요 금액: $paymentAmount")
        }

        // 7. 결제 생성
        val pendingStatus = paymentStatusTypeRepository.getPendingStatus()
        val payment = Payment.create(userId, paymentAmount, "POINT", pendingStatus)
        val savedPayment = paymentRepository.save(payment)

        try {
            // 8. 잔액 차감 (내부 메서드 직접 호출로 중첩 락 방지)
            balanceService.deductBalanceInternal(userId, paymentAmount)

            // 9. 예약 확정 (내부 메서드 직접 호출로 중첩 락 방지)
            reservationService.confirmReservationInternal(reservationId, savedPayment.paymentId)

            // 10. 좌석 상태 업데이트 (내부 메서드 직접 호출로 중첩 락 방지)
            seatService.confirmSeatInternal(seatId)

            // 11. 결제 완료 처리
            val completedStatus = paymentStatusTypeRepository.getCompletedStatus()
            val completedPayment = savedPayment.complete(completedStatus)
            val finalPayment = paymentRepository.save(completedPayment)

            // 12. 토큰 완료 처리
            tokenService.completeReservation(token)

            return PaymentDto.fromEntity(finalPayment)

        } catch (e: Exception) {
            // 결제 실패 처리
            val failedStatus = paymentStatusTypeRepository.getFailedStatus()
            val failedPayment = savedPayment.fail(failedStatus)
            paymentRepository.save(failedPayment)
            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }

    fun getPaymentById(paymentId: Long): PaymentDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException("결제를 찾을 수 없습니다: $paymentId") }

        return PaymentDto.fromEntity(payment)
    }
}
