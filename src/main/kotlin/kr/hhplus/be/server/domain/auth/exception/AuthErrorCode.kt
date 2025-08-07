package kr.hhplus.be.server.domain.auth.exception

import org.springframework.http.HttpStatus

/**
 * Auth Domain 에러 코드
 */
sealed class AuthErrorCode(
    val code: String,
    val defaultMessage: String,
    val httpStatus: HttpStatus = HttpStatus.UNAUTHORIZED
) {
    
    object TokenNotFound : AuthErrorCode(
        "TOKEN_NOT_FOUND", 
        "토큰을 찾을 수 없습니다", 
        HttpStatus.NOT_FOUND
    )
    
    object TokenExpired : AuthErrorCode(
        "TOKEN_EXPIRED", 
        "토큰이 만료되었습니다"
    )
    
    object InvalidToken : AuthErrorCode(
        "INVALID_TOKEN", 
        "유효하지 않은 토큰입니다"
    )
    
    object TokenIssuanceFailed : AuthErrorCode(
        "TOKEN_ISSUANCE_FAILED", 
        "토큰 발급에 실패했습니다", 
        HttpStatus.INTERNAL_SERVER_ERROR
    )
    
    object QueueFull : AuthErrorCode(
        "QUEUE_FULL", 
        "대기열이 가득 찼습니다", 
        HttpStatus.SERVICE_UNAVAILABLE
    )
}
