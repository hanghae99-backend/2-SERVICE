package kr.hhplus.be.server.auth.service

import kr.hhplus.be.server.user.service.UserService
import kr.hhplus.be.server.user.entity.UserNotFoundException
import org.springframework.stereotype.Component

/**
 * 도메인 검증의 단일 책임을 가진다
 * 비즈니스 규칙과 도메인 상태에 대한 검증을 담당
 */
@Component
class DomainValidator(
    private val userService: UserService
) {
    
    /**
     * 사용자 존재 여부 검증
     * 비즈니스 규칙: 존재하지 않는 사용자는 토큰 발급 불가
     */
    fun validateUserExists(userId: Long) {
        userService.findUserById(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
    }
    
    /**
     * 사용자가 토큰 발급 가능한 상태인지 검증
     * 추후 확장 가능: 사용자 상태, 권한 등 검증
     */
    fun validateTokenIssuable(userId: Long) {
        validateUserExists(userId)
        // 추후 추가 비즈니스 규칙
        // - 사용자 상태 확인 (활성/비활성)
        // - 이미 활성 토큰이 있는지 확인
        // - 사용자 권한 확인 등
    }
    
    /**
     * 사용자 활성 상태 검증
     * 비즈니스 규칙: 비활성 사용자는 서비스 이용 불가
     */
    fun validateUserActive(userId: Long) {
        validateUserExists(userId)
        // 추후 구현: 사용자 활성 상태 확인
    }
}
