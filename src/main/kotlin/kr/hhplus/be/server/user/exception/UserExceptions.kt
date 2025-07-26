package kr.hhplus.be.server.user.exception

import org.springframework.http.HttpStatus

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외
 */
data class UserNotFoundException(
    override val message: String = UserErrorCode.NotFound.defaultMessage,
    val errorCode: String = UserErrorCode.NotFound.code,
    val status: HttpStatus = UserErrorCode.NotFound.httpStatus
) : RuntimeException(message)

/**
 * 이미 존재하는 사용자일 때 발생하는 예외
 */
data class UserAlreadyExistsException(
    override val message: String = UserErrorCode.AlreadyExists.defaultMessage,
    val errorCode: String = UserErrorCode.AlreadyExists.code,
    val status: HttpStatus = UserErrorCode.AlreadyExists.httpStatus
) : RuntimeException(message)
