package kr.hhplus.be.server.user.entity

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외
 */
class UserNotFoundException(message: String) : RuntimeException(message)

/**
 * 이미 존재하는 사용자일 때 발생하는 예외
 */
class UserAlreadyExistsException(message: String) : RuntimeException(message)
