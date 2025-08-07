package kr.hhplus.be.server.global.exception

import org.springframework.http.HttpStatus


data class ParameterValidationException(
    override val message: String = CommonErrorCode.ParameterValidationError.defaultMessage,
    val errorCode: String = CommonErrorCode.ParameterValidationError.code,
    val status: HttpStatus = CommonErrorCode.ParameterValidationError.httpStatus
) : RuntimeException(message)
