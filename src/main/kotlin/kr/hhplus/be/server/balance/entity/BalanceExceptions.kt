package kr.hhplus.be.server.balance.entity

import kr.hhplus.be.server.global.exception.DomainException
import kr.hhplus.be.server.global.exception.ResourceNotFoundException
import kr.hhplus.be.server.global.exception.BusinessRuleViolationException

/**
 * 잔액 도메인에서 발생하는 기본 예외
 */
open class BalanceException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 잔액이 부족할 때 발생하는 예외
 */
class InsufficientBalanceException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 포인트를 찾을 수 없을 때 발생하는 예외
 */
class PointNotFoundException : ResourceNotFoundException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 유효하지 않은 포인트 금액일 때 발생하는 예외
 */
class InvalidPointAmountException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
