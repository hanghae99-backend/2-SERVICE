package kr.hhplus.be.server.domain.balance.exception

import org.springframework.http.HttpStatus


data class InsufficientBalanceException(
    override val message: String = BalanceErrorCode.InsufficientBalance.defaultMessage,
    val errorCode: String = BalanceErrorCode.InsufficientBalance.code,
    val status: HttpStatus = BalanceErrorCode.InsufficientBalance.httpStatus
) : RuntimeException(message)


data class InvalidPointAmountException(
    override val message: String = BalanceErrorCode.InvalidPointAmount.defaultMessage,
    val errorCode: String = BalanceErrorCode.InvalidPointAmount.code,
    val status: HttpStatus = BalanceErrorCode.InvalidPointAmount.httpStatus
) : RuntimeException(message)


data class PointNotFoundException(
    override val message: String = BalanceErrorCode.PointNotFound.defaultMessage,
    val errorCode: String = BalanceErrorCode.PointNotFound.code,
    val status: HttpStatus = BalanceErrorCode.PointNotFound.httpStatus
) : RuntimeException(message)
