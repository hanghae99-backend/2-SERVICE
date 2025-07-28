package kr.hhplus.be.server.domain.user.repository

import kr.hhplus.be.server.domain.user.model.User

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun existsById(id: Long): Boolean
    fun findAll(): List<User>
    fun delete(user: User)
}
