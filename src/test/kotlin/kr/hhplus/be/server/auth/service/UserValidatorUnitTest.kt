package kr.hhplus.be.server.auth.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.user.entity.UserNotFoundException
import kr.hhplus.be.server.user.repository.UserRepository
import kr.hhplus.be.server.user.service.UserDomainValidator
import kr.hhplus.be.server.user.service.UserParameterValidator
import kr.hhplus.be.server.user.service.UserService

class UserValidatorUnitTest : BehaviorSpec({
    lateinit var userService: UserService
    lateinit var userValidator: UserValidator
    lateinit var userDomainValidator: UserDomainValidator
    lateinit var userParameterValidator: UserParameterValidator
    beforeTest {
        userService = mockk()
        userDomainValidator = mockk()
        userParameterValidator = mockk()
        userValidator = UserValidator(userService, userDomainValidator, userParameterValidator)
        clearMocks(userService, answers = false, recordedCalls = true)
    }

    given("UserValidator는 사용자 검증의 단일 책임을 가진다") {
        `when`("사용자 존재 검증을 요청받으면") {
            then("UserService에 위임하여 존재 여부를 확인한다") {
                // given
                val userId = 1L
                val user = User.Companion.create(userId)
                every { userService.findUserById(userId) } returns user

                // when & then - UserValidator의 책임: 존재 검증
                userValidator.validateTokenIssuable(userId) // 예외 없이 통과

                verify(exactly = 1) { userService.findUserById(userId) }
            }
        }

        `when`("존재하지 않는 사용자에 대해 검증하면") {
            then("비즈니스 규칙에 따라 예외를 발생시킨다") {
                // given
                val userId = 999L
                every { userService.findUserById(userId) } returns null

                // when & then - UserValidator의 책임: 비즈니스 규칙 적용
                val exception = shouldThrow<UserNotFoundException> {
                    userValidator.validateTokenIssuable(userId)
                }

                exception.message?.contains("사용자를 찾을 수 없습니다") shouldBe true
                verify(exactly = 1) { userService.findUserById(userId) }
            }
        }

        `when`("토큰 발급 가능성 검증을 요청받으면") {
            then("사용자 존재 여부와 추가 비즈니스 규칙을 모두 검증한다") {
                // given
                val userId = 1L
                val user = User.Companion.create(userId)
                every { userService.findUserById(userId) } returns user

                // when & then - UserValidator의 책임: 종합적 검증
                userValidator.validateTokenIssuable(userId) // 예외 없이 통과

                verify(exactly = 1) { userService.findUserById(userId) }
                // 추후 추가 규칙들도 여기서 검증될 예정
            }
        }
    }
})