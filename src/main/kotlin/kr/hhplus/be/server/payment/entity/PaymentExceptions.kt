package kr.hhplus.be.server.payment.entity

import kr.hhplus.be.server.global.exception.DomainException
import kr.hhplus.be.server.global.exception.ResourceNotFoundException
import kr.hhplus.be.server.global.exception.BusinessRuleViolationException

/**
 * 결제 도메인에서 발생하는 기본 예외
 */
open class PaymentException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 결제를 찾을 수 없을 때 발생하는 예외
 */
class PaymentNotFoundException : ResourceNotFoundException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 이미 처리된 결제에 대한 작업 시 발생하는 예외
 */
class PaymentAlreadyProcessedException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 결제 처리 실패 시 발생하는 예외
 */
class PaymentProcessException : PaymentException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 예약을 찾을 수 없을 때 발생하는 예외
 */
class ReservationNotFoundException : ResourceNotFoundException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 예약이 만료되었을 때 발생하는 예외
 */
class ReservationExpiredException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 유효하지 않은 예약 상태일 때 발생하는 예외
 */
class InvalidReservationStatusException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
