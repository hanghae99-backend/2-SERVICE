package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.entity.InvalidSeatNumberException
import kr.hhplus.be.server.concert.entity.InvalidConcertDateException
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 콘서트 파라미터 검증의 단일 책임을 가진다
 * 입력값 형식, 범위 등 기본적인 검증을 담당
 */
@Component
class ConcertParameterValidator {
    
    /**
     * 콘서트 ID 파라미터 검증
     */
    fun validateConcertId(concertId: Long) {
        require(concertId > 0) { "콘서트 ID는 0보다 커야 합니다: $concertId" }
    }
    
    /**
     * 좌석 ID 파라미터 검증
     */
    fun validateSeatId(seatId: Long) {
        require(seatId > 0) { "좌석 ID는 0보다 커야 합니다: $seatId" }
    }
    
    /**
     * 좌석 번호 파라미터 검증 (1~50번)
     */
    fun validateSeatNumber(seatNumber: Int) {
        if (seatNumber < 1 || seatNumber > 50) {
            throw InvalidSeatNumberException("좌석 번호는 1~50 사이여야 합니다: $seatNumber")
        }
    }
    
    /**
     * 예약 ID 파라미터 검증
     */
    fun validateReservationId(reservationId: Long) {
        require(reservationId > 0) { "예약 ID는 0보다 커야 합니다: $reservationId" }
    }
    
    /**
     * 콘서트 날짜 파라미터 검증
     */
    fun validateConcertDate(concertDate: LocalDate) {
        val today = LocalDate.now()
        if (concertDate.isBefore(today)) {
            throw InvalidConcertDateException("콘서트 날짜는 오늘 이후여야 합니다: $concertDate")
        }
    }
    
    /**
     * 사용자 ID 파라미터 검증
     */
    fun validateUserId(userId: Long) {
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다: $userId" }
    }
}
