package kr.hhplus.be.server.user.service

import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.user.entity.UserNotFoundException
import org.springframework.stereotype.Component

/**
 * 사용자 도메인 검증의 단일 책임을 가진다
 * 비즈니스 규칙과 도메인 상태에 대한 검증을 담당
 */
@Component
class UserDomainValidator {
    
    /**
     * 사용자 존재 여부 검증
     */
    fun validateUserExists(user: User?) {
        if (user == null) {
            throw UserNotFoundException("사용자를 찾을 수 없습니다")
        }
    }
    
    /**
     * 사용자 ID로 존재 여부 검증
     */
    fun validateUserExistsById(user: User?, userId: Long) {
        if (user == null) {
            throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        }
    }
    
    /**
     * 사용자 활성 상태 검증
     * 추후 User 엔티티에 status 필드가 추가되면 구현
     */
    fun validateUserActive(user: User) {
        // 현재는 존재하면 활성으로 간주
        // 추후 User.status 필드 추가 시 구현
    }
    
    /**
     * 서비스 이용 가능 상태 검증
     */
    fun validateServiceAvailable(user: User) {
        validateUserExists(user)
        validateUserActive(user)
    }
}
