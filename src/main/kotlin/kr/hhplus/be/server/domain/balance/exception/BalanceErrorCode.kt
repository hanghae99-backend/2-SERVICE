package kr.hhplus.be.server.domain.balance.exception

import org.springframework.http.HttpStatus

/**
 * Balance Domain 에러 코드
 */
sealed class BalanceErrorCode(
    val code: String,
    val defaultMessage: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) {
    
    object InsufficientBalance : BalanceErrorCode(
        "INSUFFICIENT_BALANCE", 
        "잔액이 부족합니다"
    )
    
    object InvalidPointAmount : BalanceErrorCode(
        "INVALID_POINT_AMOUNT", 
        "유효하지 않은 포인트 금액입니다"
    )
    
    object PointNotFound : BalanceErrorCode(
        "POINT_NOT_FOUND", 
        "포인트 정보를 찾을 수 없습니다", 
        HttpStatus.NOT_FOUND
    )
}
