package kr.hhplus.be.server.domain.auth.models

import com.fasterxml.jackson.annotation.JsonIgnore
import kr.hhplus.be.server.global.exception.ParameterValidationException
import java.time.LocalDateTime

data class WaitingToken(
    val token: String,
    val userId: Long,
    val issuedAt: LocalDateTime = LocalDateTime.now(),
    val expiresAt: LocalDateTime? = null
) {
    
    companion object {
        private const val MIN_TOKEN_LENGTH = 10
        private const val MAX_TOKEN_LENGTH = 100
        
        fun create(token: String, userId: Long, expiresAt: LocalDateTime? = null): WaitingToken {
            validateParameters(token, userId)
            
            return WaitingToken(
                token = token.trim(),
                userId = userId,
                expiresAt = expiresAt
            )
        }
        
        private fun validateParameters(token: String, userId: Long) {
            if (token.isBlank()) {
                throw ParameterValidationException("토큰은 필수입니다")
            }
            if (token.length < MIN_TOKEN_LENGTH || token.length > MAX_TOKEN_LENGTH) {
                throw ParameterValidationException("토큰 길이는 ${MIN_TOKEN_LENGTH}-${MAX_TOKEN_LENGTH}자 사이여야 합니다")
            }
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
        }
    }
    
    @JsonIgnore
    fun isValidToken(): Boolean {
        return token.isNotBlank() && !isExpired()
    }
    
    @JsonIgnore
    fun isExpired(): Boolean {
        return expiresAt?.isBefore(LocalDateTime.now()) ?: false
    }
    
    @JsonIgnore
    fun belongsToUser(targetUserId: Long): Boolean {
        return userId == targetUserId
    }
    
    @JsonIgnore
    fun getRemainingTimeMinutes(): Long? {
        return expiresAt?.let {
            val duration = java.time.Duration.between(LocalDateTime.now(), it)
            if (duration.isNegative) 0L else duration.toMinutes()
        }
    }
    
    @JsonIgnore
    fun getTokenAge(): Long {
        return java.time.Duration.between(issuedAt, LocalDateTime.now()).toMinutes()
    }
}

enum class TokenStatus {
    WAITING, 
    ACTIVE, 
    EXPIRED,
    USED
}
