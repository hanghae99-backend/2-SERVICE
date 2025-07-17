package kr.hhplus.be.server.payment.service

import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.balance.service.BalanceService
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.payment.entity.*
import kr.hhplus.be.server.payment.repository.PaymentRepository
import kr.hhplus.be.server.payment.repository.ReservationRepository
import kr.hhplus.be.server.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val reservationRepository: ReservationRepository,
    private val balanceService: BalanceService,
    private val tokenService: TokenService,
    private val seatService: SeatService,
    private val userService: UserService
) {
    
    @Transactional
    fun processPayment(userId: Long, reservationId: Long, token: String): Payment {
        // 1. 토큰 검증
        if (!tokenService.isValidToken(token)) {
            throw IllegalArgumentException("유효하지 않은 토큰입니다: $token")
        }
        
        // 2. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw kr.hhplus.be.server.user.entity.UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 3. 예약 정보 조회
        val reservation = reservationRepository.findById(reservationId)
            .orElseThrow { ReservationNotFoundException("예약을 찾을 수 없습니다: $reservationId") }
        
        // 4. 예약 유효성 검증
        if (reservation.userId != userId) {
            throw IllegalArgumentException("예약의 사용자가 일치하지 않습니다")
        }
        
        if (!reservation.isTemporary()) {
            throw InvalidReservationStatusException("임시 예약 상태가 아닙니다: $reservationId")
        }
        
        if (reservation.isExpired()) {
            throw ReservationExpiredException("예약이 만료되었습니다: $reservationId")
        }
        
        // 5. 중복 결제 방지
        if (paymentRepository.existsByReservationId(reservationId)) {
            throw PaymentAlreadyProcessedException("이미 결제가 진행된 예약입니다: $reservationId")
        }
        
        // 6. 좌석 정보 조회 및 가격 확인
        val seat = seatService.findSeatById(reservation.seatId)
        val paymentAmount = seat.price
        
        // 7. 잔액 확인
        if (!balanceService.checkBalance(userId, paymentAmount)) {
            throw kr.hhplus.be.server.balance.entity.InsufficientBalanceException("잔액이 부족합니다")
        }
        
        // 8. 결제 생성
        val payment = Payment.create(userId, reservationId, paymentAmount)
        val savedPayment = paymentRepository.save(payment)
        
        try {
            // 9. 잔액 차감
            balanceService.deductBalance(userId, paymentAmount)
            
            // 10. 예약 확정
            val confirmedReservation = reservation.confirm()
            reservationRepository.save(confirmedReservation)
            
            // 11. 좌석 소유권 배정
            seatService.assignSeatToUser(reservation.seatId, userId)
            
            // 12. 결제 완료 처리
            val completedPayment = savedPayment.complete()
            val finalPayment = paymentRepository.save(completedPayment)
            
            // 13. 토큰 만료 처리
            tokenService.expireToken(token)
            
            return finalPayment
            
        } catch (e: Exception) {
            // 결제 실패 처리
            val failedPayment = savedPayment.fail()
            paymentRepository.save(failedPayment)
            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }
    
    fun getPaymentHistory(userId: Long): List<Payment> {
        // 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw kr.hhplus.be.server.user.entity.UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
    
    fun getPaymentById(paymentId: Long): Payment {
        return paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException("결제를 찾을 수 없습니다: $paymentId") }
    }
    
    fun getPaymentByReservationId(reservationId: Long): Payment? {
        return paymentRepository.findByReservationId(reservationId)
    }
}
