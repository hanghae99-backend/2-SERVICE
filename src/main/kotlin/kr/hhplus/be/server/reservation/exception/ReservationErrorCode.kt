package kr.hhplus.be.server.reservation.exception

import org.springframework.http.HttpStatus

/**
 * Reservation Domain 에러 코드
 */
sealed class ReservationErrorCode(
    val code: String,
    val defaultMessage: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) {
    
    object NotFound : ReservationErrorCode(
        "RESERVATION_NOT_FOUND", 
        "예약 정보를 찾을 수 없습니다", 
        HttpStatus.NOT_FOUND
    )
    
    object Expired : ReservationErrorCode(
        "RESERVATION_EXPIRED", 
        "예약이 만료되었습니다", 
        HttpStatus.GONE
    )
    
    object InvalidStatus : ReservationErrorCode(
        "INVALID_RESERVATION_STATUS", 
        "유효하지 않은 예약 상태입니다"
    )
    
    object AlreadyCancelled : ReservationErrorCode(
        "RESERVATION_ALREADY_CANCELLED", 
        "이미 취소된 예약입니다", 
        HttpStatus.CONFLICT
    )
}
