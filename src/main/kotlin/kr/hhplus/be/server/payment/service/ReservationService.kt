package kr.hhplus.be.server.payment.service

import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.concert.entity.SeatStatus
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.payment.entity.Reservation
import kr.hhplus.be.server.payment.repository.ReservationRepository
import kr.hhplus.be.server.user.service.UserService
import kr.hhplus.be.server.user.entity.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val tokenService: TokenService,
    private val userService: UserService,
) {
    
    @Transactional
    fun reserveSeat(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        // 1. 토큰 검증 (활성 토큰인지 확인)
        tokenService.validateActiveToken(token)
        
        // 2. 사용자 존재 확인
        val userExists = userService.existsById(userId)
        if (!userExists) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 3. 좌석 정보 조회
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw kr.hhplus.be.server.concert.entity.SeatNotFoundException("좌석을 찾을 수 없습니다")
        
        // 4. 좌석이 해당 콘서트에 속하는지 확인
        if (seat.concertId != concertId) {
            throw kr.hhplus.be.server.concert.entity.InvalidSeatNumberException("해당 콘서트의 좌석이 아닙니다")
        }
        
        // 5. 좌석 예약 가능 여부 확인 (동시성 제어를 위해 비관적 락 사용)
        val lockResult = seatJpaRepository.findByIdWithLock(seatId)
        if (lockResult == null || lockResult.status != SeatStatus.AVAILABLE) {
            throw kr.hhplus.be.server.concert.entity.SeatAlreadyReservedException("이미 예약된 좌석이거나 예약할 수 없는 좌석입니다")
        }
        
        // 6. 기존 임시 예약이 있는지 확인
        val existingReservation = reservationRepository.findBySeatIdAndStatus(
            seatId, 
            kr.hhplus.be.server.payment.entity.ReservationStatus.TEMPORARY
        )
        if (existingReservation != null && !existingReservation.isExpired()) {
            throw kr.hhplus.be.server.concert.entity.SeatTemporarilyHoldException("좌석이 임시 점유 중입니다")
        }
        
        // 7. 만료된 임시 예약이 있다면 취소 처리
        if (existingReservation != null && existingReservation.isExpired()) {
            val cancelledReservation = existingReservation.cancel()
            reservationRepository.save(cancelledReservation)
        }
        
        // 8. 좌석 상태를 RESERVED로 변경 (임시 배정)
        seatJpaRepository.updateSeatStatus(seatId, SeatStatus.RESERVED)
        
        // 10. 예약 생성 (Entity에서 검증 처리, 5분간 임시 배정)
        val expiresAt = LocalDateTime.now().plusMinutes(5)
        val reservation = Reservation.create(userId, seatId, expiresAt)
        
        return reservationRepository.save(reservation)
    }
    
    fun getReservationsByUserId(userId: Long): List<Reservation> {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
    
    fun getReservationById(reservationId: Long): Reservation {
        return reservationRepository.findById(reservationId)
            .orElseThrow { kr.hhplus.be.server.payment.entity.ReservationNotFoundException("예약을 찾을 수 없습니다: $reservationId") }
    }
    
    @Transactional
    fun cancelExpiredReservations() {
        // 만료된 임시 예약들을 찾아서 취소 처리
        val expiredReservations = reservationRepository.findExpiredTemporaryReservations(LocalDateTime.now())
        
        expiredReservations.forEach { reservation ->
            try {
                // 예약 취소 (Entity에서 상태 검증 처리)
                val cancelledReservation = reservation.cancel()
                reservationRepository.save(cancelledReservation)
                
                // 좌석 상태를 AVAILABLE로 복원
                seatJpaRepository.updateSeatStatus(reservation.seatId, SeatStatus.AVAILABLE)
            } catch (e: Exception) {
                // 로그 남기고 계속 진행
                println("만료된 예약 처리 중 오류: ${reservation.reservationId}, ${e.message}")
            }
        }
    }
}
