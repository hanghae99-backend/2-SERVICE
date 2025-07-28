package kr.hhplus.be.server.global.lock

/**
 * 동시 접근으로 인한 락 획득 실패 예외
 */
class ConcurrentAccessException(message: String) : RuntimeException(message)