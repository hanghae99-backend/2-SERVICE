package kr.hhplus.be.server.domain.balance.exception

import kr.hhplus.be.server.global.exception.DomainException
import org.springframework.http.HttpStatus
import java.math.BigDecimal

abstract class BalanceException(
    message: String,
    errorCode: String,
    httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : DomainException("BALANCE", message, errorCode, httpStatus, cause)

class PointNotFoundException(userId: Long)
    : BalanceException(
        message = "포인트 정보를 찾을 수 없습니다: $userId",
        errorCode = "NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND
    )

class InsufficientBalanceException(
    userId: Long, 
    currentBalance: BigDecimal, 
    requiredAmount: BigDecimal
) : BalanceException(
        message = "사용자 $userId 의 잔액이 부족합니다. 현재: $currentBalance, 필요: $requiredAmount",
        errorCode = "INSUFFICIENT",
        httpStatus = HttpStatus.PAYMENT_REQUIRED
    )

class InvalidAmountException(amount: BigDecimal)
    : BalanceException(
        message = "유효하지 않은 금액입니다: $amount",
        errorCode = "INVALID_AMOUNT",
        httpStatus = HttpStatus.BAD_REQUEST
    )

class PointHistoryCreationException(userId: Long, amount: BigDecimal, reason: String)
    : BalanceException(
        message = "사용자 $userId 의 포인트 이력 생성에 실패했습니다. 금액: $amount, 사유: $reason",
        errorCode = "HISTORY_CREATION_FAILED",
        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
    )
