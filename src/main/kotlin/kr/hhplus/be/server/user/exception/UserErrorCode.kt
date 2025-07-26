package kr.hhplus.be.server.user.exception

import org.springframework.http.HttpStatus

/**
 * User Domain 에러 코드
 */
sealed class UserErrorCode(
    val code: String,
    val defaultMessage: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) {
    
    object NotFound : UserErrorCode(
        "USER_NOT_FOUND", 
        "사용자를 찾을 수 없습니다", 
        HttpStatus.NOT_FOUND
    )
    
    object AlreadyExists : UserErrorCode(
        "USER_ALREADY_EXISTS", 
        "이미 존재하는 사용자입니다", 
        HttpStatus.CONFLICT
    )
}
