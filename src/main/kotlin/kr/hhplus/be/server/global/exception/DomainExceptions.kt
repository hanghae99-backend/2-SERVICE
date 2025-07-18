package kr.hhplus.be.server.global.exception

/**
 * 도메인 예외의 기본 클래스
 * 모든 도메인 예외는 이 클래스를 상속받아야 함
 */
abstract class DomainException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 파라미터 검증 실패 시 발생하는 예외
 */
class ParameterValidationException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 도메인 검증 실패 시 발생하는 예외
 */
open class DomainValidationException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 리소스를 찾을 수 없을 때 발생하는 예외
 */
open class ResourceNotFoundException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * 비즈니스 규칙 위반 시 발생하는 예외
 */
open class BusinessRuleViolationException : DomainException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
