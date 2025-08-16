package kr.hhplus.be.server.domain.concert.exception

import kr.hhplus.be.server.global.exception.DomainException
import org.springframework.http.HttpStatus

abstract class SeatException(
    message: String,
    errorCode: String,
    httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : DomainException("SEAT", message, errorCode, httpStatus, cause)

class SeatNotFoundException(seatId: Long)
    : SeatException(
        message = "좌석을 찾을 수 없습니다: $seatId",
        errorCode = "NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND
    )

class SeatAlreadyReservedException(seatId: Long)
    : SeatException(
        message = "이미 예약된 좌석입니다: $seatId",
        errorCode = "ALREADY_RESERVED",
        httpStatus = HttpStatus.CONFLICT
    )

class SeatNotAvailableException(seatId: Long, currentStatus: String)
    : SeatException(
        message = "예약할 수 없는 좌석입니다: $seatId (현재 상태: $currentStatus)",
        errorCode = "NOT_AVAILABLE",
        httpStatus = HttpStatus.CONFLICT
    )

class InvalidSeatStatusTransitionException(seatId: Long, from: String, to: String)
    : SeatException(
        message = "좌석 $seatId 의 상태를 $from 에서 $to 로 변경할 수 없습니다",
        errorCode = "INVALID_STATUS_TRANSITION",
        httpStatus = HttpStatus.BAD_REQUEST
    )
