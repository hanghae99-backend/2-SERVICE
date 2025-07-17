package kr.hhplus.be.server.user.service

import org.springframework.stereotype.Component

/**
 * 사용자 파라미터 검증의 단일 책임을 가진다
 * 입력값 형식, 범위 등 기본적인 검증을 담당
 */
@Component
class UserParameterValidator {
    
    /**
     * 사용자 ID 파라미터 검증
     */
    fun validateUserId(userId: Long) {
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다: $userId" }
    }
    
    /**
     * 사용자 이름 파라미터 검증
     */
    fun validateUserName(name: String) {
        require(name.isNotBlank()) { "사용자 이름은 비어있을 수 없습니다" }
        require(name.length <= 50) { "사용자 이름은 50자를 초과할 수 없습니다" }
    }
    
    /**
     * 이메일 파라미터 검증
     */
    fun validateEmail(email: String) {
        require(email.isNotBlank()) { "이메일은 비어있을 수 없습니다" }
        require(email.contains("@")) { "올바른 이메일 형식이 아닙니다" }
        require(email.length <= 100) { "이메일은 100자를 초과할 수 없습니다" }
    }
}
