package kr.hhplus.be.server.domain.user.models

import kr.hhplus.be.server.global.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_users_created_at", columnList = "created_at"),
        Index(name = "idx_users_status", columnList = "status")
    ]
)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    var userId: Long = 0,
    
    @Column(name = "name", nullable = false, length = 100)
    var name: String = "User$userId",
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE
) : BaseEntity() {

    companion object {
        fun create(userId: Long = 0, name: String? = null): User {
            // userId가 0이면 JPA에서 자동 생성, 0이 아니면 직접 지정
            val actualName = if (userId > 0) {
                name ?: "User$userId"
            } else {
                name ?: "User"
            }
            
            return User(
                userId = userId,
                name = actualName
            )
        }
        
        // 테스트용: 특정 ID를 가진 사용자 생성
        fun createWithId(userId: Long, name: String? = null): User {
            if (userId <= 0) {
                throw kr.hhplus.be.server.global.exception.ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            
            return User(
                userId = userId,
                name = name ?: "User$userId"
            )
        }
    }
    
    fun activate() {
        if (status == UserStatus.SUSPENDED) {
            throw IllegalStateException("정지된 사용자는 활성화할 수 없습니다")
        }
        this.status = UserStatus.ACTIVE
    }
    
    fun deactivate() {
        this.status = UserStatus.INACTIVE
    }
    
    fun suspend(reason: String? = null) {
        this.status = UserStatus.SUSPENDED
    }
    
    fun updateProfile(name: String?) {
        name?.let { 
            if (it.isNotBlank()) this.name = it 
        }
    }
    
    fun isActive(): Boolean = status == UserStatus.ACTIVE
    fun isInactive(): Boolean = status == UserStatus.INACTIVE
    fun isSuspended(): Boolean = status == UserStatus.SUSPENDED
    fun canPerformActions(): Boolean = status == UserStatus.ACTIVE
}

enum class UserStatus {
    ACTIVE,
    INACTIVE, 
    SUSPENDED
}