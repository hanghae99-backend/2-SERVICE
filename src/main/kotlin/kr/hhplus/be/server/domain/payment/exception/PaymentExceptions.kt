package kr.hhplus.be.server.domain.payment.exception

import kr.hhplus.be.server.global.exception.DomainException
import org.springframework.http.HttpStatus

abstract class PaymentException(
    message: String,
    errorCode: String,
    httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : DomainException("PAYMENT", message, errorCode, httpStatus, cause)

class PaymentNotFoundException(paymentId: Long)
    : PaymentException(
        message = "결제를 찾을 수 없습니다: $paymentId",
        errorCode = "NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND
    )

class PaymentProcessException(message: String, cause: Throwable? = null)
    : PaymentException(
        message = message,
        errorCode = "PROCESS_FAILED",
        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
        cause = cause
    )

class PaymentAlreadyCompletedException(paymentId: Long)
    : PaymentException(
        message = "이미 완료된 결제입니다: $paymentId",
        errorCode = "ALREADY_COMPLETED",
        httpStatus = HttpStatus.CONFLICT
    )

class PaymentAmountValidationException(message: String)
    : PaymentException(
        message = message,
        errorCode = "AMOUNT_VALIDATION_FAILED",
        httpStatus = HttpStatus.BAD_REQUEST
    )

class InvalidPaymentStatusTransitionException(paymentId: Long, from: String, to: String)
    : PaymentException(
        message = "결제 $paymentId 의 상태를 $from 에서 $to 로 변경할 수 없습니다",
        errorCode = "INVALID_STATUS_TRANSITION",
        httpStatus = HttpStatus.BAD_REQUEST
    )
