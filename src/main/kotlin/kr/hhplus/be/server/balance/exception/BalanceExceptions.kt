package kr.hhplus.be.server.balance.exception

import org.springframework.http.HttpStatus

/**
 * 잔액이 부족할 때 발생하는 예외
 */
data class InsufficientBalanceException(
    override val message: String = BalanceErrorCode.InsufficientBalance.defaultMessage,
    val errorCode: String = BalanceErrorCode.InsufficientBalance.code,
    val status: HttpStatus = BalanceErrorCode.InsufficientBalance.httpStatus
) : RuntimeException(message)

/**
 * 유효하지 않은 포인트 금액일 때 발생하는 예외
 */
data class InvalidPointAmountException(
    override val message: String = BalanceErrorCode.InvalidPointAmount.defaultMessage,
    val errorCode: String = BalanceErrorCode.InvalidPointAmount.code,
    val status: HttpStatus = BalanceErrorCode.InvalidPointAmount.httpStatus
) : RuntimeException(message)

/**
 * 포인트를 찾을 수 없을 때 발생하는 예외
 */
data class PointNotFoundException(
    override val message: String = BalanceErrorCode.PointNotFound.defaultMessage,
    val errorCode: String = BalanceErrorCode.PointNotFound.code,
    val status: HttpStatus = BalanceErrorCode.PointNotFound.httpStatus
) : RuntimeException(message)
