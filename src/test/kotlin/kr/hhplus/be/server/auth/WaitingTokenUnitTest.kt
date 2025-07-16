package kr.hhplus.be.server.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class WaitingTokenUnitTest : BehaviorSpec({
    given("WaitingToken 도메인") {
        When("WaitingToken을 생성할 때") {
            Then("정상적으로 생성된다") {
                val token = "test-token"
                val userId = 1L
                val waitingToken = WaitingToken(token, userId)
                
                waitingToken.token shouldBe token
                waitingToken.userId shouldBe userId
            }
        }
        When("같은 속성을 가진 두 개의 토큰이 있을 때") {
            Then("동등하다고 판단된다") {
                val token1 = WaitingToken("test-token", 1L)
                val token2 = WaitingToken("test-token", 1L)
                token1 shouldBe token2
            }
        }
        When("다른 속성을 가진 두 개의 토큰이 있을 때") {
            Then("동등하지 않다고 판단된다") {
                val token1 = WaitingToken("test-token-1", 1L)
                val token2 = WaitingToken("test-token-2", 1L)
                token1 shouldNotBe token2
            }
        }
        When("다양한 Long userId 값으로 토큰을 생성할 때") {
            Then("정상적으로 생성된다") {
                val token1 = WaitingToken("token1", 1L)
                token1.userId shouldBe 1L
                
                val token2 = WaitingToken("token2", 999L)
                token2.userId shouldBe 999L
                
                val token3 = WaitingToken("token3", Long.MAX_VALUE)
                token3.userId shouldBe Long.MAX_VALUE
            }
        }
        When("copy()로 토큰의 일부 속성을 변경할 때") {
            Then("변경된 속성을 가진 복사본이 생성된다") {
                val originalToken = WaitingToken("test-token", 1L)
                val newUserId = 2L
                val copiedToken = originalToken.copy(userId = newUserId)
                
                copiedToken.userId shouldBe newUserId
                copiedToken.token shouldBe originalToken.token
            }
        }
    }

    given("TokenStatus Enum") {
        When("TokenStatus enum의 모든 값을 확인할 때") {
            Then("정의된 값의 수와 일치해야 한다") {
                TokenStatus.values().size shouldBe 3
                TokenStatus.values() shouldBe arrayOf(
                    TokenStatus.WAITING,
                    TokenStatus.ACTIVE,
                    TokenStatus.EXPIRED
                )
            }
        }
        When("TokenStatus enum 값들의 이름을 확인할 때") {
            Then("올바른 이름을 가져야 한다") {
                TokenStatus.WAITING.name shouldBe "WAITING"
                TokenStatus.ACTIVE.name shouldBe "ACTIVE"
                TokenStatus.EXPIRED.name shouldBe "EXPIRED"
            }
        }
    }
})
