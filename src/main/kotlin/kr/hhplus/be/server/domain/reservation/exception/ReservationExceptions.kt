package kr.hhplus.be.server.domain.reservation.exception

import kr.hhplus.be.server.global.exception.DomainException
import org.springframework.http.HttpStatus

abstract class ReservationException(
    message: String,
    errorCode: String,
    httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : DomainException("RESERVATION", message, errorCode, httpStatus, cause)

class ReservationNotFoundException(reservationId: Long) 
    : ReservationException(
        message = "예약을 찾을 수 없습니다: $reservationId",
        errorCode = "NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND
    )

class ReservationAlreadyConfirmedException(reservationId: Long)
    : ReservationException(
        message = "이미 확정된 예약입니다: $reservationId",
        errorCode = "ALREADY_CONFIRMED",
        httpStatus = HttpStatus.CONFLICT
    )

class ReservationExpiredException(reservationId: Long)
    : ReservationException(
        message = "만료된 예약입니다: $reservationId",
        errorCode = "EXPIRED",
        httpStatus = HttpStatus.GONE
    )

class UserReservationLimitExceededException(userId: Long, concertId: Long)
    : ReservationException(
        message = "사용자 $userId 는 콘서트 $concertId 에 이미 예약이 있습니다",
        errorCode = "USER_LIMIT_EXCEEDED",
        httpStatus = HttpStatus.CONFLICT
    )

class ConcurrentReservationLimitExceededException
    : ReservationException(
        message = "현재 예약이 집중되어 잠시 후 다시 시도해주세요",
        errorCode = "CONCURRENT_LIMIT_EXCEEDED",
        httpStatus = HttpStatus.TOO_MANY_REQUESTS
    )

class ReservationAccessDeniedException(userId: Long, reservationId: Long)
    : ReservationException(
        message = "사용자 $userId 는 예약 $reservationId 에 대한 권한이 없습니다",
        errorCode = "ACCESS_DENIED",
        httpStatus = HttpStatus.FORBIDDEN
    )
