package kr.hhplus.be.server.payment.exception

import org.springframework.http.HttpStatus

/**
 * Payment Domain 에러 코드
 */
sealed class PaymentErrorCode(
    val code: String,
    val defaultMessage: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) {
    
    object NotFound : PaymentErrorCode(
        "PAYMENT_NOT_FOUND", 
        "결제 정보를 찾을 수 없습니다", 
        HttpStatus.NOT_FOUND
    )
    
    object AlreadyProcessed : PaymentErrorCode(
        "PAYMENT_ALREADY_PROCESSED", 
        "이미 처리된 결제입니다", 
        HttpStatus.CONFLICT
    )
    
    object ProcessFailed : PaymentErrorCode(
        "PAYMENT_PROCESS_FAILED", 
        "결제 처리에 실패했습니다", 
        HttpStatus.BAD_REQUEST
    )
}
