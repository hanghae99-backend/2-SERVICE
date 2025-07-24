package kr.hhplus.be.server.user.service

import kr.hhplus.be.server.user.dto.UserDto
import kr.hhplus.be.server.user.dto.UserDetail
import kr.hhplus.be.server.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.user.exception.UserAlreadyExistsException
import kr.hhplus.be.server.user.exception.UserNotFoundException
import kr.hhplus.be.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun createUser(userCreateRequest: UserCreateRequest): UserDto {
        val userId = userCreateRequest.userId
        if (userRepository.existsById(userId)) {
            throw UserAlreadyExistsException("이미 존재하는 사용자 ID입니다: $userId")
        }
        
        val user = User.create(userId)
        val savedUser = userRepository.save(user)

        return UserDto.fromEntity(savedUser)
    }

    fun getUserById(userId: Long): User? {
        return userRepository.findById(userId).orElse(null)
    }

    fun getUserDetailById(userId: Long): UserDetail {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("사용자를 찾을 수 없습니다: $userId") }
        return UserDetail.fromEntity(user)
    }

    fun existsById(userId: Long): Boolean {
        return userRepository.existsById(userId)
    }

    fun getUserCount(): Long {
        return userRepository.count()
    }
    
    fun getUserDtoById(userId: Long): UserDto? {
        val user = getUserById(userId) ?: return null
        return UserDto.fromEntity(user)
    }
}
