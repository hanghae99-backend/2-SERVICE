package kr.hhplus.be.server.payment.exception

import org.springframework.http.HttpStatus

/**
 * 결제를 찾을 수 없을 때 발생하는 예외
 */
data class PaymentNotFoundException(
    override val message: String = PaymentErrorCode.NotFound.defaultMessage,
    val errorCode: String = PaymentErrorCode.NotFound.code,
    val status: HttpStatus = PaymentErrorCode.NotFound.httpStatus
) : RuntimeException(message)

/**
 * 이미 처리된 결제에 대한 작업 시 발생하는 예외
 */
data class PaymentAlreadyProcessedException(
    override val message: String = PaymentErrorCode.AlreadyProcessed.defaultMessage,
    val errorCode: String = PaymentErrorCode.AlreadyProcessed.code,
    val status: HttpStatus = PaymentErrorCode.AlreadyProcessed.httpStatus
) : RuntimeException(message)

/**
 * 결제 처리 실패 시 발생하는 예외
 */
data class PaymentProcessException(
    override val message: String = PaymentErrorCode.ProcessFailed.defaultMessage,
    val errorCode: String = PaymentErrorCode.ProcessFailed.code,
    val status: HttpStatus = PaymentErrorCode.ProcessFailed.httpStatus
) : RuntimeException(message)
