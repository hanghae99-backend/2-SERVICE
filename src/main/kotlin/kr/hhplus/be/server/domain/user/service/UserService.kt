package kr.hhplus.be.server.domain.user.service

import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.api.user.dto.UserDto
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.domain.user.exception.UserAlreadyExistsException
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.user.repositories.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun createUser(userCreateRequest: UserCreateRequest): UserDto {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(userCreateRequest.email)) {
            throw UserAlreadyExistsException("이미 존재하는 이메일입니다: ${userCreateRequest.email}")
        }
        
        val user = User.create(
            userId = 0, // JPA에서 자동 생성
            name = userCreateRequest.name,
            email = userCreateRequest.email
        )
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
