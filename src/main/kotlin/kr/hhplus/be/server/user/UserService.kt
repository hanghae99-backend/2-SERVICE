package kr.hhplus.be.server.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

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
        
        val user = User.create(userId)
        return userRepository.save(user)
    }
    
    fun findUserById(userId: Long): User? {
        return userRepository.findById(userId).orElse(null)
    }
    
    fun getAllUsers(): List<User> {
        return userRepository.findAll()
    }
    
    @Transactional
    fun deleteUser(userId: Long): Boolean {
        return if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId)
            true
        } else {
            false
        }
    }
    
    fun existsById(userId: Long): Boolean {
        return userRepository.existsById(userId)
    }
    
    fun getUserCount(): Long {
        return userRepository.count()
    }
    
    fun getUserById(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("사용자를 찾을 수 없습니다: $userId") }
    }
}

class UserNotFoundException(message: String) : RuntimeException(message)
class UserAlreadyExistsException(message: String) : RuntimeException(message)
