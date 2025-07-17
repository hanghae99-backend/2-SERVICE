package kr.hhplus.be.server.balance.entity

/**
 * 잔액이 부족할 때 발생하는 예외
 */
class InsufficientBalanceException(message: String) : RuntimeException(message)

/**
 * 포인트를 찾을 수 없을 때 발생하는 예외
 */
class PointNotFoundException(message: String) : RuntimeException(message)

/**
 * 유효하지 않은 포인트 금액일 때 발생하는 예외
 */
class InvalidPointAmountException(message: String) : RuntimeException(message)
