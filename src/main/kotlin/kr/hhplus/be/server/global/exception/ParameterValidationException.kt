package kr.hhplus.be.server.global.exception

import org.springframework.http.HttpStatus

/**
 * 파라미터 검증 실패 시 발생하는 예외
 */
data class ParameterValidationException(
    override val message: String = CommonErrorCode.ParameterValidationError.defaultMessage,
    val errorCode: String = CommonErrorCode.ParameterValidationError.code,
    val status: HttpStatus = CommonErrorCode.ParameterValidationError.httpStatus
) : RuntimeException(message)
