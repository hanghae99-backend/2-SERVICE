package kr.hhplus.be.server.user.repository

import kr.hhplus.be.server.user.entity.User

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
}
