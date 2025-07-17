package kr.hhplus.be.server.auth.entity

/**
 * 토큰을 찾을 수 없을 때 발생하는 예외
 */
class TokenNotFoundException(message: String) : RuntimeException(message)

/**
 * 토큰이 만료되었을 때 발생하는 예외
 */
class TokenExpiredException(message: String) : RuntimeException(message)

/**
 * 토큰 활성화 실패 시 발생하는 예외
 */
class TokenActivationException(message: String) : RuntimeException(message)

/**
 * 유효하지 않은 토큰일 때 발생하는 예외
 */
class InvalidTokenException(message: String) : RuntimeException(message)

/**
 * 대기열이 가득 찰 때 발생하는 예외
 */
class QueueFullException(message: String) : RuntimeException(message)

/**
 * 토큰이 이미 활성화되어 있을 때 발생하는 예외
 */
class TokenAlreadyActiveException(message: String) : RuntimeException(message)
