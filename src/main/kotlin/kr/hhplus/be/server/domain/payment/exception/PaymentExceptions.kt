package kr.hhplus.be.server.domain.payment.exception

import org.springframework.http.HttpStatus


data class PaymentNotFoundException(
    override val message: String = PaymentErrorCode.NotFound.defaultMessage,
    val errorCode: String = PaymentErrorCode.NotFound.code,
    val status: HttpStatus = PaymentErrorCode.NotFound.httpStatus
) : RuntimeException(message)


data class PaymentAlreadyProcessedException(
    override val message: String = PaymentErrorCode.AlreadyProcessed.defaultMessage,
    val errorCode: String = PaymentErrorCode.AlreadyProcessed.code,
    val status: HttpStatus = PaymentErrorCode.AlreadyProcessed.httpStatus
) : RuntimeException(message)


data class PaymentProcessException(
    override val message: String = PaymentErrorCode.ProcessFailed.defaultMessage,
    val errorCode: String = PaymentErrorCode.ProcessFailed.code,
    val status: HttpStatus = PaymentErrorCode.ProcessFailed.httpStatus
) : RuntimeException(message)
