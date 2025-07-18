package kr.hhplus.be.server.user.service

import kr.hhplus.be.server.global.exception.ParameterValidationException
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
        if (userId <= 0) {
            throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
        }
    }
}
