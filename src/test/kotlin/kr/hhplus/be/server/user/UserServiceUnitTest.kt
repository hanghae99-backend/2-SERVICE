package kr.hhplus.be.server.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import java.util.*

class UserServiceUnitTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>(relaxed = true)
    val userService = UserService(userRepository)

    beforeTest {
        clearMocks(userRepository, answers = false, recordedCalls = true)
    }

    given("UserService") {
        When("새로운 사용자를 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val user = User.create(userId)
                every { userRepository.existsById(userId) } returns false
                every { userRepository.save(any<User>()) } returns user

                val result = userService.createUser(userId)

                result.userId shouldBe userId
                result.createdAt.shouldNotBeNull()
                verify(exactly = 1) { userRepository.existsById(userId) }
                verify(exactly = 1) { userRepository.save(any<User>()) }
            }
            Then("중복된 사용자 ID면 예외가 발생한다") {
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

        When("사용자 ID로 조회할 때") {
            Then("존재하면 반환한다") {
                val userId = 1L
                val user = User.create(userId)
                every { userRepository.findById(userId) } returns Optional.of(user)

                val result = userService.findUserById(userId)
                result.shouldNotBeNull()
                result!!.userId shouldBe userId
                verify(exactly = 1) { userRepository.findById(userId) }
            }
            Then("존재하지 않으면 null 반환") {
                val userId = 999L
                every { userRepository.findById(userId) } returns Optional.empty()

                val result = userService.findUserById(userId)
                result.shouldBeNull()
                verify(exactly = 1) { userRepository.findById(userId) }
            }
        }

        When("사용자를 삭제할 때") {
            Then("존재하면 삭제 후 true 반환") {
                val userId = 1L
                every { userRepository.existsById(userId) } returns true
                every { userRepository.deleteById(userId) } just Runs

                val result = userService.deleteUser(userId)
                result shouldBe true
                verify(exactly = 1) { userRepository.existsById(userId) }
                verify(exactly = 1) { userRepository.deleteById(userId) }
            }
            Then("존재하지 않으면 false 반환") {
                val userId = 999L
                every { userRepository.existsById(userId) } returns false

                val result = userService.deleteUser(userId)
                result shouldBe false
                verify(exactly = 1) { userRepository.existsById(userId) }
                verify(exactly = 0) { userRepository.deleteById(any()) }
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
})
