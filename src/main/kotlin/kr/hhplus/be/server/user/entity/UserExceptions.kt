package kr.hhplus.be.server.user.entity

import kr.hhplus.be.server.global.exception.DomainException
import kr.hhplus.be.server.global.exception.ResourceNotFoundException
import kr.hhplus.be.server.global.exception.BusinessRuleViolationException

/**
 * 사용자 도메인에서 발생하는 기본 예외
 */
open class UserException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외
 */
class UserNotFoundException : ResourceNotFoundException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 이미 존재하는 사용자일 때 발생하는 예외
 */
class UserAlreadyExistsException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
