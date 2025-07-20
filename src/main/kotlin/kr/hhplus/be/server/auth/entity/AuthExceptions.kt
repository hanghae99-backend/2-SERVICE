package kr.hhplus.be.server.auth.entity

import kr.hhplus.be.server.global.exception.DomainException
import kr.hhplus.be.server.global.exception.ResourceNotFoundException
import kr.hhplus.be.server.global.exception.BusinessRuleViolationException
import kr.hhplus.be.server.global.exception.DomainValidationException

/**
 * 인증 도메인에서 발생하는 기본 예외
 */
open class AuthException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 토큰을 찾을 수 없을 때 발생하는 예외
 */
class TokenNotFoundException : ResourceNotFoundException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 토큰 활성화 실패 시 발생하는 예외
 */
class TokenActivationException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 토큰 발급 실패 시 발생하는 예외
 */
class TokenIssuanceException : BusinessRuleViolationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 사용자 검증 실패 시 발생하는 예외 (Auth 도메인 내부용)
 */
class UserValidationException : DomainValidationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message)
}
