package kr.hhplus.be.server.payment.service

import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.balance.service.BalanceService
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.payment.entity.*
import kr.hhplus.be.server.payment.repository.PaymentRepository
import kr.hhplus.be.server.payment.repository.ReservationRepository
import kr.hhplus.be.server.user.service.UserService
import kr.hhplus.be.server.user.entity.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val reservationRepository: ReservationRepository,
    private val balanceService: BalanceService,
    private val tokenService: TokenService,
    private val seatService: SeatService,
    private val userService: UserService,
    private val parameterValidator: PaymentParameterValidator
) {
    
    @Transactional
    fun processPayment(userId: Long, reservationId: Long, token: String): Payment {
        // 1. 파라미터 검증
        parameterValidator.validateUserId(userId)
        parameterValidator.validateReservationId(reservationId)
        parameterValidator.validateToken(token)
        
        // 2. 토큰 검증 (Auth 도메인에 위임)
        tokenService.validateActiveToken(token)
        
        // 3. 사용자 존재 확인
        val userExists = userService.existsById(userId)
        if (!userExists) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 4. 예약 정보 조회
        val reservation = reservationRepository.findById(reservationId)
            .orElseThrow { ReservationNotFoundException("예약을 찾을 수 없습니다: $reservationId") }
        
        // 5. 예약 유효성 검증
        if (reservation.userId != userId) {
            throw PaymentProcessException("예약의 사용자가 일치하지 않습니다")
        }
        if (!reservation.isTemporary()) {
            throw InvalidReservationStatusException("임시 예약 상태가 아닙니다: $reservationId")
        }
        if (reservation.isExpired()) {
            throw ReservationExpiredException("예약이 만료되었습니다: $reservationId")
        }
        
        // 6. 중복 결제 방지
        val existingPayment = paymentRepository.findByReservationId(reservationId)
        if (existingPayment != null) {
            throw PaymentAlreadyProcessedException("이미 결제가 진행된 예약입니다: $reservationId")
        }
        
        // 7. 좌석 정보 조회 및 가격 확인
        val seat = seatService.getSeatById(reservation.seatId)
        val paymentAmount = seat.price
        
        // 8. 잔액 확인
        val hasEnoughBalance = balanceService.checkBalance(userId, paymentAmount)
        if (!hasEnoughBalance) {
            throw kr.hhplus.be.server.balance.entity.InsufficientBalanceException("잔액이 부족합니다")
        }
        
        // 9. 결제 생성 (Entity에서 검증 처리)
        val payment = Payment.create(userId, reservationId, paymentAmount)
        val savedPayment = paymentRepository.save(payment)
        
        try {
            // 10. 잔액 차감
            balanceService.deductBalance(userId, paymentAmount)
            
            // 11. 예약 확정 (Entity에서 상태 검증 처리)
            val confirmedReservation = reservation.confirm()
            reservationRepository.save(confirmedReservation)
            
            // 12. 좌석 상태 업데이트
            seatService.updateSeatStatus(reservation.seatId, kr.hhplus.be.server.concert.entity.SeatStatus.CONFIRMED)
            
            // 13. 결제 완료 처리 (Entity에서 상태 검증 처리)
            val completedPayment = savedPayment.complete()
            val finalPayment = paymentRepository.save(completedPayment)
            
            // 14. 토큰 완료 처리
            tokenService.completeReservation(token)
            
            return finalPayment
            
        } catch (e: Exception) {
            // 결제 실패 처리 (Entity에서 상태 검증 처리)
            val failedPayment = savedPayment.fail()
            paymentRepository.save(failedPayment)
            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }
    
    fun getPaymentHistory(userId: Long): List<Payment> {
        // 파라미터 검증
        parameterValidator.validateUserId(userId)
        
        // 사용자 존재 확인
        val userExists = userService.existsById(userId)
        if (!userExists) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
    
    fun getPaymentById(paymentId: Long): Payment {
        // 파라미터 검증
        parameterValidator.validatePaymentId(paymentId)
        
        return paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException("결제를 찾을 수 없습니다: $paymentId") }
    }
    
    fun getPaymentByReservationId(reservationId: Long): Payment? {
        // 파라미터 검증
        parameterValidator.validateReservationId(reservationId)
        
        return paymentRepository.findByReservationId(reservationId)
    }
}
