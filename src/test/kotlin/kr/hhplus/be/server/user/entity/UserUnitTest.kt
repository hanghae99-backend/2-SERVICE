package kr.hhplus.be.server.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import kr.hhplus.be.server.user.entity.User
import java.time.LocalDateTime

class UserUnitTest : BehaviorSpec({
    given("User 도메인") {
        When("User.create()로 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val user = User.create(userId)
                user.userId shouldBe userId
                user.createdAt.shouldNotBeNull()
                user.updatedAt.shouldNotBeNull()
                user.createdAt.isBefore(LocalDateTime.now()) || user.createdAt.isEqual(LocalDateTime.now()) shouldBe true
                user.updatedAt.isBefore(LocalDateTime.now()) || user.updatedAt.isEqual(LocalDateTime.now()) shouldBe true
            }
        }
        When("생성자로 직접 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val createdAt = LocalDateTime.now().minusHours(1)
                val updatedAt = LocalDateTime.now()
                val user = User(userId, createdAt, updatedAt)
                user.userId shouldBe userId
                user.createdAt shouldBe createdAt
                user.updatedAt shouldBe updatedAt
            }
        }
        When("같은 userId로 User를 2개 생성할 때") {
            Then("동등성 비교가 true다") {
                val userId = 1L
                val user1 = User.create(userId)
                val user2 = User.create(userId)
                user1 shouldBe user2
                user1.hashCode() shouldBe user2.hashCode()
            }
        }
        When("다른 userId로 User를 2개 생성할 때") {
            Then("동등성 비교가 false다") {
                val user1 = User.create(1L)
                val user2 = User.create(2L)
                user1.equals(user2) shouldBe false
                user1.hashCode().equals(user2.hashCode()) shouldBe false
            }
        }
    }
})
