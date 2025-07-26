package kr.hhplus.be.server.user.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.reservation.entity.Reservation
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
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
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            
            return User(
                userId = userId
            )
        }
    }
}
