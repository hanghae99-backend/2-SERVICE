package kr.hhplus.be.server.domain.concert.exception

import org.springframework.http.HttpStatus


data class ConcertNotFoundException(
    override val message: String = ConcertErrorCode.NotFound.defaultMessage,
    val errorCode: String = ConcertErrorCode.NotFound.code,
    val status: HttpStatus = ConcertErrorCode.NotFound.httpStatus
) : RuntimeException(message)


data class InvalidSeatStatusException(
    override val message: String = ConcertErrorCode.InvalidSeatStatus.defaultMessage,
    val errorCode: String = ConcertErrorCode.InvalidSeatStatus.code,
    val status: HttpStatus = ConcertErrorCode.InvalidSeatStatus.httpStatus
) : RuntimeException(message)

/**
 * 좌석을 찾을 수 없을 때 발생하는 예외
 */
data class SeatNotFoundException(
    override val message: String = ConcertErrorCode.SeatNotFound.defaultMessage,
    val errorCode: String = ConcertErrorCode.SeatNotFound.code,
    val status: HttpStatus = ConcertErrorCode.SeatNotFound.httpStatus
) : RuntimeException(message)

/**
 * 좌석이 이미 예약되어 있을 때 발생하는 예외
 */
data class SeatAlreadyReservedException(
    override val message: String = ConcertErrorCode.SeatAlreadyReserved.defaultMessage,
    val errorCode: String = ConcertErrorCode.SeatAlreadyReserved.code,
    val status: HttpStatus = ConcertErrorCode.SeatAlreadyReserved.httpStatus
) : RuntimeException(message)

/**
 * 콘서트 스케줄을 찾을 수 없을 때 발생하는 예외
 */
data class ConcertScheduleNotFoundException(
    override val message: String = ConcertErrorCode.ScheduleNotFound.defaultMessage,
    val errorCode: String = ConcertErrorCode.ScheduleNotFound.code,
    val status: HttpStatus = ConcertErrorCode.ScheduleNotFound.httpStatus
) : RuntimeException(message)
