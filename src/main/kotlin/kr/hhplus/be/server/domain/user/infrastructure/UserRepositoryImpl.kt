package kr.hhplus.be.server.domain.user.infrastructure

import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.repository.UserRepository
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository
) : UserRepository {

    override fun save(user: User): User {
        return userJpaRepository.save(user)
    }

    override fun findById(id: Long): User? {
        return userJpaRepository.findById(id).orElse(null)
    }

    override fun existsById(id: Long): Boolean {
        return userJpaRepository.existsById(id)
    }

    override fun findAll(): List<User> {
        return userJpaRepository.findAll()
    }

    override fun delete(user: User) {
        userJpaRepository.delete(user)
    }
}