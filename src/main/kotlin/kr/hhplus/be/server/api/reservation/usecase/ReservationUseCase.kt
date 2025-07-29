package kr.hhplus.be.server.api.reservation.usecase

import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.api.auth.usecase.TokenUseCase
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationSearchCondition
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockKeyManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
@Transactional(readOnly = true)
class ReservationUseCase(
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val userService: UserService,
    private val tokenUseCase: TokenUseCase,
    private val distributedLock: DistributedLock
) {

    @Transactional
    fun reserveSeat(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        val lockKey = LockKeyManager.seatOperation(seatId)
        
        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 10000L,
            waitTimeoutMs = 5000L
        ) {
            reserveSeatInternal(userId, concertId, seatId, token)
        }
    }
    
    private fun reserveSeatInternal(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {

        tokenUseCase.validateActiveToken(token)
        

        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        

        if (!seatService.isSeatAvailable(seatId)) {
            throw IllegalStateException("예약할 수 없는 좌석입니다: $seatId")
        }
        

        return reservationService.reserveSeatInternal(userId, concertId, seatId)
    }
    
    @Transactional
    fun confirmReservation(reservationId: Long, paymentId: Long): Reservation {
        return reservationService.confirmReservation(reservationId, paymentId)
    }
    
    @Transactional
    fun cancelReservation(reservationId: Long, userId: Long, cancelReason: String?, token: String): Reservation {

        tokenUseCase.validateActiveToken(token)
        

        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
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
