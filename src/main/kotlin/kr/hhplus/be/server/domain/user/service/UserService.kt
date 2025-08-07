package kr.hhplus.be.server.domain.user.service

import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.api.user.dto.UserDto
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.exception.UserAlreadyExistsException
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.user.repository.UserRepository
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
        return userRepository.findById(userId)
    }

    fun existsById(userId: Long): Boolean {
        return userRepository.existsById(userId)
    }

    fun getUserDtoById(userId: Long): UserDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User with id $userId not found") }
        return UserDto.fromEntity(user)
    }
}
