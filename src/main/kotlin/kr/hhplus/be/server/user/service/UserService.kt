package kr.hhplus.be.server.user.service

import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.user.entity.UserAlreadyExistsException
import kr.hhplus.be.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun createUser(userId: Long): User {
        if (userRepository.existsById(userId)) {
            throw UserAlreadyExistsException("이미 존재하는 사용자 ID입니다: $userId")
        }
        
        // Entity의 create 메소드에서 파라미터 검증 처리
        val user = User.create(userId)
        return userRepository.save(user)
    }
    
    fun findUserById(userId: Long): User? {
        return userRepository.findById(userId).orElse(null)
    }
    
    fun getUserById(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow { throw IllegalArgumentException("사용자를 찾을 수 없습니다: $userId") }
    }
    
    fun getAllUsers(): List<User> {
        return userRepository.findAll()
    }

    fun existsById(userId: Long): Boolean {
        return userRepository.existsById(userId)
    }
    
    fun getUserCount(): Long {
        return userRepository.count()
    }
}
