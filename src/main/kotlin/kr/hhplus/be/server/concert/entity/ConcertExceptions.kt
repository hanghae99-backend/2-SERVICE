package kr.hhplus.be.server.concert.entity

import kr.hhplus.be.server.global.exception.DomainException
import kr.hhplus.be.server.global.exception.ResourceNotFoundException
import kr.hhplus.be.server.global.exception.BusinessRuleViolationException

/**
 * 콘서트 도메인에서 발생하는 기본 예외
 */
open class ConcertException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 콘서트를 찾을 수 없을 때 발생하는 예외
 */
class ConcertNotFoundException : ResourceNotFoundException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 좌석을 찾을 수 없을 때 발생하는 예외
 */
class SeatNotFoundException : ResourceNotFoundException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 좌석이 이미 예약되어 있을 때 발생하는 예외
 */
class SeatAlreadyReservedException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 좌석이 임시 배정 상태일 때 발생하는 예외
 */
class SeatTemporarilyHoldException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 유효하지 않은 좌석 번호일 때 발생하는 예외
 */
class InvalidSeatNumberException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 콘서트 날짜가 유효하지 않을 때 발생하는 예외
 */
class InvalidConcertDateException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 좌석 상태가 유효하지 않을 때 발생하는 예외
 */
class InvalidSeatStatusException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
