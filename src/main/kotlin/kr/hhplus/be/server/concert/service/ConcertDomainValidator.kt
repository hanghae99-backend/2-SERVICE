package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.entity.*
import kr.hhplus.be.server.payment.entity.Reservation
import kr.hhplus.be.server.payment.entity.ReservationStatus
import kr.hhplus.be.server.payment.entity.ReservationExpiredException
import kr.hhplus.be.server.payment.entity.InvalidReservationStatusException
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 콘서트 도메인 검증의 단일 책임을 가진다
 * 비즈니스 규칙과 도메인 상태에 대한 검증을 담당
 */
@Component
class ConcertDomainValidator {
    
    /**
     * 콘서트 존재 여부 검증
     */
    fun validateConcertExists(concert: Concert?) {
        if (concert == null) {
            throw ConcertNotFoundException("콘서트를 찾을 수 없습니다")
        }
    }
    
    /**
     * 좌석 존재 여부 검증
     */
    fun validateSeatExists(seat: Seat?) {
        if (seat == null) {
            throw SeatNotFoundException("좌석을 찾을 수 없습니다")
        }
    }
    
    /**
     * 좌석 예약 가능 여부 검증
     */
    fun validateSeatAvailable(seat: Seat) {
        when (seat.status) {
            SeatStatus.RESERVED -> throw SeatAlreadyReservedException("이미 예약된 좌석입니다")
            SeatStatus.CONFIRMED -> throw SeatAlreadyReservedException("이미 확정된 좌석입니다")
            SeatStatus.UNAVAILABLE -> throw SeatTemporarilyHoldException("예약 불가능한 좌석입니다")
            SeatStatus.AVAILABLE -> return // 예약 가능
        }
    }
    
    /**
     * 콘서트 날짜 유효성 검증
     */
    fun validateConcertDate(concert: Concert) {
        val today = LocalDate.now()
        if (concert.concertDate.isBefore(today)) {
            throw InvalidConcertDateException("이미 지난 콘서트입니다")
        }
    }
    
    /**
     * 콘서트와 좌석의 연관성 검증
     */
    fun validateSeatBelongsToConcert(concert: Concert, seat: Seat) {
        if (seat.concertId != concert.concertId) {
            throw InvalidSeatNumberException("해당 콘서트의 좌석이 아닙니다")
        }
    }
    
    /**
     * 예약 만료 시간 검증
     */
    fun validateReservationNotExpired(reservation: Reservation) {
        if (reservation.isExpired()) {
            throw ReservationExpiredException("예약이 만료되었습니다")
        }
    }
    
    /**
     * 예약 상태 검증
     */
    fun validateReservationStatus(reservation: Reservation, expectedStatus: ReservationStatus) {
        if (reservation.status != expectedStatus) {
            throw InvalidReservationStatusException(
                "예약 상태가 올바르지 않습니다. 예상: $expectedStatus, 실제: ${reservation.status}"
            )
        }
    }
    
    /**
     * 임시 예약 상태 검증
     */
    fun validateTemporaryReservation(reservation: Reservation) {
        validateReservationStatus(reservation, ReservationStatus.TEMPORARY)
        validateReservationNotExpired(reservation)
    }
}
