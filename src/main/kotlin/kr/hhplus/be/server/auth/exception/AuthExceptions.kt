package kr.hhplus.be.server.auth.exception

import org.springframework.http.HttpStatus

/**
 * 토큰을 찾을 수 없을 때 발생하는 예외
 */
data class TokenNotFoundException(
    override val message: String = AuthErrorCode.TokenNotFound.defaultMessage,
    val errorCode: String = AuthErrorCode.TokenNotFound.code,
    val status: HttpStatus = AuthErrorCode.TokenNotFound.httpStatus
) : RuntimeException(message)

/**
 * 토큰이 만료되었을 때 발생하는 예외
 */
data class TokenExpiredException(
    override val message: String = AuthErrorCode.TokenExpired.defaultMessage,
    val errorCode: String = AuthErrorCode.TokenExpired.code,
    val status: HttpStatus = AuthErrorCode.TokenExpired.httpStatus
) : RuntimeException(message)

/**
 * 유효하지 않은 토큰일 때 발생하는 예외
 */
data class InvalidTokenException(
    override val message: String = AuthErrorCode.InvalidToken.defaultMessage,
    val errorCode: String = AuthErrorCode.InvalidToken.code,
    val status: HttpStatus = AuthErrorCode.InvalidToken.httpStatus
) : RuntimeException(message)

/**
 * 토큰 발급 실패 시 발생하는 예외
 */
data class TokenIssuanceException(
    override val message: String = AuthErrorCode.TokenIssuanceFailed.defaultMessage,
    val errorCode: String = AuthErrorCode.TokenIssuanceFailed.code,
    val status: HttpStatus = AuthErrorCode.TokenIssuanceFailed.httpStatus
) : RuntimeException(message)

/**
 * 대기열이 가득 찰 때 발생하는 예외
 */
data class QueueFullException(
    override val message: String = AuthErrorCode.QueueFull.defaultMessage,
    val errorCode: String = AuthErrorCode.QueueFull.code,
    val status: HttpStatus = AuthErrorCode.QueueFull.httpStatus
) : RuntimeException(message)

/**
 * 대기열이 가득 찰 때 발생하는 예외
 */
data class TokenActivationException(
    override val message: String = AuthErrorCode.TokenExpired.defaultMessage,
    val errorCode: String = AuthErrorCode.TokenExpired.code,
    val status: HttpStatus = AuthErrorCode.TokenExpired.httpStatus
) : RuntimeException(message)
