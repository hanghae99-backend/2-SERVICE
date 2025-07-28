package kr.hhplus.be.server.domain.auth.models

import kr.hhplus.be.server.global.exception.ParameterValidationException

data class WaitingToken(
    val token: String,
    val userId: Long
) {
    
    companion object {
        fun create(token: String, userId: Long): WaitingToken {
            // 파라미터 검증
            if (token.isBlank()) {
                throw ParameterValidationException("토큰은 필수입니다")
            }
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            
            return WaitingToken(
                token = token,
                userId = userId
            )
        }
    }
    
    fun isValidToken(): Boolean {
        return token.isNotBlank()
    }
    
    fun belongsToUser(targetUserId: Long): Boolean {
        return userId == targetUserId
    }
}

enum class TokenStatus {
    WAITING, ACTIVE, EXPIRED
}
