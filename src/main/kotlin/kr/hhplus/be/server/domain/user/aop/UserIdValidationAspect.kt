package kr.hhplus.be.server.domain.user.aop

import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class UserIdValidationAspect(
    private val userService: UserService
) {
    
    private val logger = LoggerFactory.getLogger(UserIdValidationAspect::class.java)
    
    @Before("@annotation(validateUserId)")
    fun validateUserId(joinPoint: JoinPoint, validateUserId: ValidateUserId) {
        try {
            val userId = extractUserId(joinPoint, validateUserId)
            
            if (userId == null) {
                if (!validateUserId.nullable) {
                    throw UserNotFoundException("userId는 필수입니다.")
                }
                return
            }
            
            if (userId <= 0) {
                throw UserNotFoundException("유효하지 않은 userId입니다: $userId")
            }
            
            if (!userService.existsById(userId)) {
                throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
            }
            
        } catch (e: UserNotFoundException) {
            throw e
        } catch (e: Exception) {
            throw UserNotFoundException("사용자 검증 중 오류가 발생했습니다.")
        }
    }
    
    private fun extractUserId(joinPoint: JoinPoint, validateUserId: ValidateUserId): Long? {
        val args = joinPoint.args
        val methodSignature = joinPoint.signature as MethodSignature
        val method = methodSignature.method
        val parameters = method.parameters
        
        val parameterName = validateUserId.parameterName
        
        for (i in parameters.indices) {
            val parameter = parameters[i]
            val paramName = parameter.name
            
            if (paramName == parameterName) {
                val argValue = args[i]
                
                return when {
                    argValue == null -> null
                    argValue is Long -> argValue
                    parameterName == "userId" && argValue is Number -> argValue.toLong()
                    else -> {
                        try {
                            val userIdField = argValue::class.java.getDeclaredField("userId")
                            userIdField.isAccessible = true
                            val userId = userIdField.get(argValue)
                            when (userId) {
                                null -> null
                                is Long -> userId
                                is Number -> userId.toLong()
                                else -> throw IllegalArgumentException("userId must be Long type")
                            }
                        } catch (e: Exception) {
                            throw UserNotFoundException("userId 파라미터를 찾을 수 없습니다: $parameterName")
                        }
                    }
                }
            }
        }
        
        throw UserNotFoundException("userId 파라미터를 찾을 수 없습니다: $parameterName")
    }
}
