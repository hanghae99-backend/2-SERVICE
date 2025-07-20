package kr.hhplus.be.server.user.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user")
data class User(
    @Id
    @Column(name = "user_id")
    val userId: Long,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    companion object {
        fun create(userId: Long): User {
            // 파라미터 검증
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            
            return User(
                userId = userId
            )
        }
    }
}
