package kr.hhplus.be.server.domain.user.model

import kr.hhplus.be.server.global.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Id
    @Column(name = "user_id")
    val userId: Long
) : BaseEntity() {

    companion object {
        fun create(userId: Long): User {
            if (userId <= 0) {
                throw kr.hhplus.be.server.global.exception.ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }

            return User(userId = userId)
        }
    }
}