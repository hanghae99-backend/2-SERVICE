package kr.hhplus.be.server.domain.user.exception

import org.springframework.http.HttpStatus


data class UserNotFoundException(
    override val message: String = UserErrorCode.NotFound.defaultMessage,
    val errorCode: String = UserErrorCode.NotFound.code,
    val status: HttpStatus = UserErrorCode.NotFound.httpStatus
) : RuntimeException(message)


data class UserAlreadyExistsException(
    override val message: String = UserErrorCode.AlreadyExists.defaultMessage,
    val errorCode: String = UserErrorCode.AlreadyExists.code,
    val status: HttpStatus = UserErrorCode.AlreadyExists.httpStatus
) : RuntimeException(message)
