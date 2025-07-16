package kr.hhplus.be.server.concert.entity

/**
 * 콘서트를 찾을 수 없을 때 발생하는 예외
 */
class ConcertNotFoundException(message: String) : RuntimeException(message)

/**
 * 좌석을 찾을 수 없을 때 발생하는 예외
 */
class SeatNotFoundException(message: String) : RuntimeException(message)
