package kr.hhplus.be.server.domain.user.model

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "users")
class User(
    @jakarta.persistence.Id
    @jakarta.persistence.Column(name = "user_id")
    val userId: Long,

    @jakarta.persistence.Column(name = "created_at", nullable = false)
    val createdAt: java.time.LocalDateTime = java.time.LocalDateTime.now(),

    @jakarta.persistence.Column(name = "updated_at", nullable = false)
    val updatedAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
) {

    companion object {
        fun create(userId: Long): User {
            if (userId <= 0) {
                throw kr.hhplus.be.server.global.exception.ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }

            return User(
                userId = userId
            )
        }
    }
}