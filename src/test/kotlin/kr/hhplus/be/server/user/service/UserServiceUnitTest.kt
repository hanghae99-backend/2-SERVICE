package kr.hhplus.be.server.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.user.entity.UserAlreadyExistsException
import kr.hhplus.be.server.user.repository.UserRepository
import kr.hhplus.be.server.user.service.UserService
import java.util.*

class UserServiceUnitTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>(relaxed = true)
    val userService = UserService(userRepository)

    beforeTest {
        clearMocks(userRepository, answers = false, recordedCalls = true)
    }

    given("UserService는 사용자 생명주기 관리의 책임을 가진다") {
        When("비즈니스 규칙에 따라 사용자 생성을 요청받으면") {
            Then("중복 검증 후 새로운 사용자를 생성한다") {
                val userId = 1L
                val user = User.create(userId)
                every { userRepository.existsById(userId) } returns false
                every { userRepository.save(any<User>()) } returns user

                val result = userService.createUser(userId)

                // then - UserService의 책임: 비즈니스 규칙 전략 + 사용자 생성
                result.userId shouldBe userId
                result.createdAt.shouldNotBeNull()

                // 비즈니스 규칙: 중복 검증 후 생성
                verify(exactly = 1) { userRepository.existsById(userId) }
                verify(exactly = 1) { userRepository.save(any<User>()) }
            }
            Then("비즈니스 규칙 위반 시 예외를 발생시킨다") {
                val userId = 1L
                every { userRepository.existsById(userId) } returns true

                val exception = shouldThrow<UserAlreadyExistsException> {
                    userService.createUser(userId)
                }
                exception.message?.contains("이미 존재하는 사용자 ID입니다") shouldBe true
                verify(exactly = 1) { userRepository.existsById(userId) }
                verify(exactly = 0) { userRepository.save(any<User>()) }
            }
            Then("경계값(최소값)도 정상 생성된다") {
                val userId = 1L
                val user = User.create(userId)
                every { userRepository.existsById(userId) } returns false
                every { userRepository.save(any<User>()) } returns user

                val result = userService.createUser(userId)
                result.userId shouldBe 1L
            }
            Then("경계값(큰 값)도 정상 생성된다") {
                val userId = Long.MAX_VALUE
                val user = User.create(userId)
                every { userRepository.existsById(userId) } returns false
                every { userRepository.save(any<User>()) } returns user

                val result = userService.createUser(userId)
                result.userId shouldBe Long.MAX_VALUE
            }
        }

        given("UserService는 사용자 조회 및 상태 관리의 책임을 가진다") {
            When("사용자 조회를 요청받으면") {
                Then("저장소에 위임하여 사용자를 찾는다") {
                    val userId = 1L
                    val user = User.create(userId)
                    every { userRepository.findById(userId) } returns Optional.of(user)

                    val result = userService.findUserById(userId)
                    // then - UserService의 책임: 저장소 위임
                    result.shouldNotBeNull()
                    result!!.userId shouldBe userId
                    verify(exactly = 1) { userRepository.findById(userId) }

                    // 사용자가 없는 경우도 처리
                    every { userRepository.findById(999L) } returns Optional.empty()
                    val nullResult = userService.findUserById(999L)
                    nullResult.shouldBeNull()
                }
            }

            When("모든 사용자를 조회할 때") {
                Then("전체 리스트를 반환한다") {
                    val users = listOf(User.create(1L), User.create(2L))
                    every { userRepository.findAll() } returns users

                    val result = userService.getAllUsers()
                    result.size shouldBe 2
                    result shouldBe users
                    verify(exactly = 1) { userRepository.findAll() }
                }
            }

            When("사용자 존재 여부를 확인할 때") {
                Then("존재하면 true 반환") {
                    val userId = 1L
                    every { userRepository.existsById(userId) } returns true

                    val result = userService.existsById(userId)
                    result shouldBe true
                    verify(exactly = 1) { userRepository.existsById(userId) }
                }
            }

            When("사용자 수를 확인할 때") {
                Then("정확한 수를 반환한다") {
                    every { userRepository.count() } returns 5L

                    val result = userService.getUserCount()
                    result shouldBe 5L
                    verify(exactly = 1) { userRepository.count() }
                }
            }
        }
    }
})
