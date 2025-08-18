package kr.hhplus.be.server.domain.user.infrastructure

import kr.hhplus.be.server.domain.user.models.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserJpaRepository : JpaRepository<User, Long> {
    fun existsByEmail(email: String): Boolean
}