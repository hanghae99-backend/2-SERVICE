package kr.hhplus.be.server.user.service

import com.hbd.book_be.dto.UserDto
import com.hbd.book_be.dto.UserDto.Companion.fromEntity
import kr.hhplus.be.server.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.user.exception.UserAlreadyExistsException
import kr.hhplus.be.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun createUser(userCreateRequest: UserCreateRequest): UserDto {
        val userId = userCreateRequest.userId;
        if (userRepository.existsById(userId)) {
            throw UserAlreadyExistsException("이미 존재하는 사용자 ID입니다: $userId")
        }
        
        val user = User.create(userId)
        userRepository.save(user)

        return fromEntity(user)
    }

    @Transactional(readOnly = true)
    fun getUserById(userId: Long): UserDto.Detail {
        val user = userRepository.findById(userId)
            .orElseThrow { throw IllegalArgumentException("사용자를 찾을 수 없습니다: $userId") }
        return UserDto.Detail.fromEntity(user)
    }

    @Transactional(readOnly = true)
    fun getAllUsers(): List<UserDto> {
        val userList = userRepository.findAll()
        return userList.map{fromEntity(it)}
    }

    @Transactional(readOnly = true)
    fun existsById(userId: Long): Boolean {
        val isExists = userRepository.existsById(userId)
        return isExists
    }

    @Transactional(readOnly = true)
    fun getUserCount(): Long {
        return userRepository.count()
    }
}
