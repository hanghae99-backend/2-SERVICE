package kr.hhplus.be.server.domain.user.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.exception.UserAlreadyExistsException
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.user.repository.UserRepository
import kr.hhplus.be.server.domain.user.service.UserService
import java.time.LocalDateTime

class UserServiceTest : DescribeSpec({
    
    val userRepository = mockk<UserRepository>()
    val userService = UserService(userRepository)
    
    describe("createUser") {
        context("유효한 사용자 생성 요청이 들어올 때") {
            it("새로운 사용자를 생성하고 UserDto를 반환해야 한다") {
                // given
                val userId = 1L
                val userCreateRequest = UserCreateRequest(userId)
                val user = User(
                    userId = userId
                )
                
                every { userRepository.existsById(userId) } returns false
                every { userRepository.save(any()) } returns user
                
                // when
                val result = userService.createUser(userCreateRequest)
                
                // then
                result shouldNotBe null
                result.userId shouldBe userId
                verify { userRepository.existsById(userId) }
                verify { userRepository.save(any()) }
            }
        }
        
        context("이미 존재하는 사용자 ID로 생성 요청이 들어올 때") {
            it("UserAlreadyExistsException을 던져야 한다") {
                // given
                val userId = 1L
                val userCreateRequest = UserCreateRequest(userId)
                
                every { userRepository.existsById(userId) } returns true
                
                // when & then
                shouldThrow<UserAlreadyExistsException> {
                    userService.createUser(userCreateRequest)
                }
                
                verify { userRepository.existsById(userId) }
            }
        }
    }
    
    describe("getUserById") {
        context("존재하는 사용자 ID로 조회할 때") {
            it("사용자 정보를 반환해야 한다") {
                // given
                val userId = 1L
                val user = User(
                    userId = userId
                )
                
                every { userRepository.findById(userId) } returns user
                
                // when
                val result = userService.getUserById(userId)
                
                // then
                result shouldNotBe null
                result?.userId shouldBe userId
                verify { userRepository.findById(userId) }
            }
        }
        
        context("존재하지 않는 사용자 ID로 조회할 때") {
            it("null을 반환해야 한다") {
                // given
                val userId = 999L
                
                every { userRepository.findById(userId) } returns null
                
                // when
                val result = userService.getUserById(userId)
                
                // then
                result shouldBe null
                verify { userRepository.findById(userId) }
            }
        }
    }
    
    describe("existsById") {
        context("존재하는 사용자 ID로 확인할 때") {
            it("true를 반환해야 한다") {
                // given
                val userId = 1L
                
                every { userRepository.existsById(userId) } returns true
                
                // when
                val result = userService.existsById(userId)
                
                // then
                result shouldBe true
                verify { userRepository.existsById(userId) }
            }
        }
        
        context("존재하지 않는 사용자 ID로 확인할 때") {
            it("false를 반환해야 한다") {
                // given
                val userId = 999L
                
                every { userRepository.existsById(userId) } returns false
                
                // when
                val result = userService.existsById(userId)
                
                // then
                result shouldBe false
                verify { userRepository.existsById(userId) }
            }
        }
    }
})