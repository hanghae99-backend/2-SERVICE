package kr.hhplus.be.server.domain.auth.exception

import org.springframework.http.HttpStatus


data class TokenNotFoundException(
    override val message: String = AuthErrorCode.TokenNotFound.defaultMessage,
    val errorCode: String = AuthErrorCode.TokenNotFound.code,
    val status: HttpStatus = AuthErrorCode.TokenNotFound.httpStatus
) : RuntimeException(message)


data class TokenExpiredException(
    override val message: String = AuthErrorCode.TokenExpired.defaultMessage,
    val errorCode: String = AuthErrorCode.TokenExpired.code,
    val status: HttpStatus = AuthErrorCode.TokenExpired.httpStatus
) : RuntimeException(message)


data class InvalidTokenException(
    override val message: String = AuthErrorCode.InvalidToken.defaultMessage,
    val errorCode: String = AuthErrorCode.InvalidToken.code,
    val status: HttpStatus = AuthErrorCode.InvalidToken.httpStatus
) : RuntimeException(message)


data class TokenIssuanceException(
    override val message: String = AuthErrorCode.TokenIssuanceFailed.defaultMessage,
    val errorCode: String = AuthErrorCode.TokenIssuanceFailed.code,
    val status: HttpStatus = AuthErrorCode.TokenIssuanceFailed.httpStatus
) : RuntimeException(message)


data class QueueFullException(
    override val message: String = AuthErrorCode.QueueFull.defaultMessage,
    val errorCode: String = AuthErrorCode.QueueFull.code,
    val status: HttpStatus = AuthErrorCode.QueueFull.httpStatus
) : RuntimeException(message)


data class TokenActivationException(
    override val message: String = AuthErrorCode.TokenExpired.defaultMessage,
    val errorCode: String = AuthErrorCode.TokenExpired.code,
    val status: HttpStatus = AuthErrorCode.TokenExpired.httpStatus
) : RuntimeException(message)
