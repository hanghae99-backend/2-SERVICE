package kr.hhplus.be.server.concert.entity

/**
 * 콘서트를 찾을 수 없을 때 발생하는 예외
 */
class ConcertNotFoundException(message: String) : RuntimeException(message)

/**
 * 좌석을 찾을 수 없을 때 발생하는 예외
 */
class SeatNotFoundException(message: String) : RuntimeException(message)

/**
 * 좌석이 이미 예약되어 있을 때 발생하는 예외
 */
class SeatAlreadyReservedException(message: String) : RuntimeException(message)

/**
 * 좌석이 임시 배정 상태일 때 발생하는 예외
 */
class SeatTemporarilyHoldException(message: String) : RuntimeException(message)

/**
 * 유효하지 않은 좌석 번호일 때 발생하는 예외
 */
class InvalidSeatNumberException(message: String) : RuntimeException(message)

/**
 * 콘서트 날짜가 유효하지 않을 때 발생하는 예외
 */
class InvalidConcertDateException(message: String) : RuntimeException(message)
