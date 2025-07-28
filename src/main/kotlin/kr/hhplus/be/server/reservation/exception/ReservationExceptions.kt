package kr.hhplus.be.server.reservation.exception

import org.springframework.http.HttpStatus

/**
 * 예약을 찾을 수 없을 때 발생하는 예외
 */
data class ReservationNotFoundException(
    override val message: String = ReservationErrorCode.NotFound.defaultMessage,
    val errorCode: String = ReservationErrorCode.NotFound.code,
    val status: HttpStatus = ReservationErrorCode.NotFound.httpStatus
) : RuntimeException(message)

/**
 * 예약이 만료되었을 때 발생하는 예외
 */
data class ReservationExpiredException(
    override val message: String = ReservationErrorCode.Expired.defaultMessage,
    val errorCode: String = ReservationErrorCode.Expired.code,
    val status: HttpStatus = ReservationErrorCode.Expired.httpStatus
) : RuntimeException(message)

/**
 * 유효하지 않은 예약 상태일 때 발생하는 예외
 */
data class InvalidReservationStatusException(
    override val message: String = ReservationErrorCode.InvalidStatus.defaultMessage,
    val errorCode: String = ReservationErrorCode.InvalidStatus.code,
    val status: HttpStatus = ReservationErrorCode.InvalidStatus.httpStatus
) : RuntimeException(message)

/**
 * 예약이 이미 취소되었을 때 발생하는 예외
 */
data class ReservationAlreadyCancelledException(
    override val message: String = ReservationErrorCode.AlreadyCancelled.defaultMessage,
    val errorCode: String = ReservationErrorCode.AlreadyCancelled.code,
    val status: HttpStatus = ReservationErrorCode.AlreadyCancelled.httpStatus
) : RuntimeException(message)
