package kr.hhplus.be.server.concert.exception

import org.springframework.http.HttpStatus

/**
 * Concert Domain 에러 코드
 */
sealed class ConcertErrorCode(
    val code: String,
    val defaultMessage: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) {
    
    object NotFound : ConcertErrorCode(
        "CONCERT_NOT_FOUND", 
        "콘서트를 찾을 수 없습니다", 
        HttpStatus.NOT_FOUND
    )
    
    object ScheduleNotFound : ConcertErrorCode(
        "CONCERT_SCHEDULE_NOT_FOUND", 
        "콘서트 스케줄을 찾을 수 없습니다", 
        HttpStatus.NOT_FOUND
    )
    
    object SeatNotFound : ConcertErrorCode(
        "SEAT_NOT_FOUND", 
        "좌석을 찾을 수 없습니다", 
        HttpStatus.NOT_FOUND
    )
    
    object InvalidSeatStatus : ConcertErrorCode(
        "INVALID_SEAT_STATUS", 
        "유효하지 않은 좌석 상태입니다"
    )
    
    object SeatAlreadyReserved : ConcertErrorCode(
        "SEAT_ALREADY_RESERVED", 
        "이미 예약된 좌석입니다", 
        HttpStatus.CONFLICT
    )
}
