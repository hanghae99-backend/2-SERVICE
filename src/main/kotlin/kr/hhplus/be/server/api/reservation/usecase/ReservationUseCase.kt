package kr.hhplus.be.server.api.reservation.usecase

import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.api.auth.usecase.TokenUseCase
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationSearchCondition
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
@Transactional(readOnly = true)
class ReservationUseCase(
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val userService: UserService,
    private val tokenUseCase: TokenUseCase
) {

    @Transactional
    fun reserveSeat(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        // 1. 토큰 검증
        tokenUseCase.validateActiveToken(token)
        
        // 2. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 3. 좌석 가용성 확인
        if (!seatService.isSeatAvailable(seatId)) {
            throw IllegalStateException("예약할 수 없는 좌석입니다: $seatId")
        }
        
        // 4. Service의 External 메서드 호출 (분산 락 포함)
        return reservationService.reserveSeat(userId, concertId, seatId)
    }
    
    @Transactional
    fun confirmReservation(reservationId: Long, paymentId: Long): Reservation {
        return reservationService.confirmReservation(reservationId, paymentId)
    }
    
    @Transactional
    fun cancelReservation(reservationId: Long, userId: Long, cancelReason: String?, token: String): Reservation {
        // 1. 토큰 검증
        tokenUseCase.validateActiveToken(token)
        
        // 2. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 3. Service 호출
        return reservationService.cancelReservation(reservationId, userId, cancelReason)
    }
    
    fun getReservationById(reservationId: Long): Reservation {
        return reservationService.getReservationById(reservationId)
    }
    
    fun getReservationWithDetails(reservationId: Long): Reservation {
        return reservationService.getReservationWithDetails(reservationId)
    }
    
    fun getReservationsByCondition(condition: ReservationSearchCondition): ReservationDto.Page {
        return reservationService.getReservationsByCondition(condition)
    }
    
    fun getExpiredReservations(pageNumber: Int, pageSize: Int): ReservationDto.Page {
        return reservationService.getExpiredReservations(pageNumber, pageSize)
    }
    
    @Transactional
    fun cleanupExpiredReservations(): Int {
        return reservationService.cleanupExpiredReservations()
    }
}
