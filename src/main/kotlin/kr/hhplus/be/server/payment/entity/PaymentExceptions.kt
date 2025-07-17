package kr.hhplus.be.server.payment.entity

/**
 * 결제를 찾을 수 없을 때 발생하는 예외
 */
class PaymentNotFoundException(message: String) : RuntimeException(message)

/**
 * 이미 처리된 결제에 대한 작업 시 발생하는 예외
 */
class PaymentAlreadyProcessedException(message: String) : RuntimeException(message)

/**
 * 결제 처리 실패 시 발생하는 예외
 */
class PaymentProcessException(message: String) : RuntimeException(message)

/**
 * 예약을 찾을 수 없을 때 발생하는 예외
 */
class ReservationNotFoundException(message: String) : RuntimeException(message)

/**
 * 예약이 만료되었을 때 발생하는 예외
 */
class ReservationExpiredException(message: String) : RuntimeException(message)

/**
 * 유효하지 않은 예약 상태일 때 발생하는 예외
 */
class InvalidReservationStatusException(message: String) : RuntimeException(message)
