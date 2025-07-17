package kr.hhplus.be.server.auth.service

import kr.hhplus.be.server.user.service.UserService
import kr.hhplus.be.server.user.service.UserDomainValidator
import kr.hhplus.be.server.user.service.UserParameterValidator
import org.springframework.stereotype.Component

/**
 * Auth 도메인에서 사용하는 사용자 검증 통합 컴포넌트
 * 파라미터 검증과 도메인 검증을 통합하여 제공
 */
@Component
class UserValidator(
    private val userService: UserService,
    private val userDomainValidator: UserDomainValidator,
    private val userParameterValidator: UserParameterValidator
) {
    
    /**
     * 토큰 발급 가능 여부 검증
     * 1. 파라미터 검증 -> 2. 사용자 존재 검증 -> 3. 서비스 이용 가능 검증
     */
    fun validateTokenIssuable(userId: Long) {
        // 1. 파라미터 검증
        userParameterValidator.validateUserId(userId)
        
        // 2. 사용자 존재 및 상태 검증
        val user = userService.findUserById(userId)
        userDomainValidator.validateUserExistsById(user, userId)
        userDomainValidator.validateServiceAvailable(user!!)
    }
    
    /**
     * 토큰 사용 가능 여부 검증
     */
    fun validateTokenUsable(userId: Long) {
        // 파라미터 검증
        userParameterValidator.validateUserId(userId)
        
        // 사용자 존재 및 상태 검증
        val user = userService.findUserById(userId)
        userDomainValidator.validateUserExistsById(user, userId)
        userDomainValidator.validateServiceAvailable(user!!)
    }
}
