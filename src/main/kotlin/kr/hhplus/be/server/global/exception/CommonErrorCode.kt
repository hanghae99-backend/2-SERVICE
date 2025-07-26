package kr.hhplus.be.server.global.exception

import org.springframework.http.HttpStatus

/**
 * 공통 에러 코드
 */
sealed class CommonErrorCode(
    val code: String,
    val defaultMessage: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) {
    
    object InternalServerError : CommonErrorCode(
        "INTERNAL_SERVER_ERROR", 
        "서버 내부 오류가 발생했습니다", 
        HttpStatus.INTERNAL_SERVER_ERROR
    )
    
    object BadRequest : CommonErrorCode(
        "BAD_REQUEST", 
        "잘못된 요청입니다", 
        HttpStatus.BAD_REQUEST
    )
    
    object ParameterValidationError : CommonErrorCode(
        "PARAMETER_VALIDATION_ERROR", 
        "파라미터 검증에 실패했습니다", 
        HttpStatus.BAD_REQUEST
    )
    
    object RuntimeError : CommonErrorCode(
        "RUNTIME_ERROR", 
        "런타임 오류가 발생했습니다", 
        HttpStatus.INTERNAL_SERVER_ERROR
    )
}
