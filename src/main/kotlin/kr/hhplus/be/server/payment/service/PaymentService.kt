package kr.hhplus.be.server.payment.service

import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.balance.service.BalanceService
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.payment.dto.PaymentDto
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.payment.exception.PaymentProcessException
import kr.hhplus.be.server.payment.repository.PaymentRepository
import kr.hhplus.be.server.reservation.service.ReservationService
import kr.hhplus.be.server.user.service.UserService
import kr.hhplus.be.server.user.exception.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val reservationService: ReservationService,
    private val balanceService: BalanceService,
    private val tokenService: TokenService,
    private val seatService: SeatService,
    private val userService: UserService
) {

    @Transactional
    fun processPayment(userId: Long, reservationId: Long, token: String): PaymentDto {
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

        // 5. 좌석 정보 조회 - reservation에서 seatId가 nullable이므로 체크 필요
        val seatId = reservation.seatId ?: throw PaymentProcessException("예약에 좌석 정보가 없습니다")
        val seat = seatService.getSeatById(seatId)
        val paymentAmount = seat.price

        // 6. 잔액 확인 및 차감
        val currentBalance = balanceService.getBalance(userId)
        if (currentBalance.amount < paymentAmount) {
            throw PaymentProcessException("잔액이 부족합니다. 현재 잔액: ${currentBalance.amount}, 필요 금액: $paymentAmount")
        }

        // 7. 결제 생성
        val payment = Payment.create(userId, paymentAmount)
        val savedPayment = paymentRepository.save(payment)

        try {
            // 8. 잔액 차감
            balanceService.deductBalance(userId, paymentAmount)

            // 9. 예약 확정
            reservationService.confirmReservation(reservationId, savedPayment.paymentId)

            // 10. 좌석 상태 업데이트
            seatService.confirmSeat(seatId)

            // 11. 결제 완료 처리
            val completedPayment = savedPayment.complete()
            val finalPayment = paymentRepository.save(completedPayment)

            // 12. 토큰 완료 처리
            tokenService.completeReservation(token)

            return PaymentDto.fromEntity(finalPayment)

        } catch (e: Exception) {
            // 결제 실패 처리
            val failedPayment = savedPayment.fail()
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
