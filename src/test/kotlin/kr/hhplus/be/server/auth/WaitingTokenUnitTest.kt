package kr.hhplus.be.server.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.assertions.throwables.shouldThrow
import java.time.LocalDateTime

class WaitingTokenUnitTest : BehaviorSpec({
    given("WaitingToken 도메인") {
        When("WaitingToken을 생성할 때") {
            Then("정상적으로 생성된다") {
                val token = "test-token"
                val userId = 1L
                val position = 10L
                val waitingToken = WaitingToken(token, userId, position)
                waitingToken.token shouldBe token
                waitingToken.userId shouldBe userId
                waitingToken.position shouldBe position
                waitingToken.status shouldBe TokenStatus.WAITING
                waitingToken.issuedAt.shouldNotBeNull()
                waitingToken.activatedAt.shouldBeNull()
                waitingToken.expiredAt.shouldBeNull()
            }
        }
        When("WAITING 상태의 토큰을 활성화할 때") {
            Then("ACTIVE 상태로 변경되고 activatedAt이 세팅된다") {
                val waitingToken = WaitingToken("test-token", 1L, 1L, TokenStatus.WAITING)
                val activatedToken = waitingToken.activate()
                activatedToken.status shouldBe TokenStatus.ACTIVE
                activatedToken.activatedAt.shouldNotBeNull()
                activatedToken.activatedAt!!.isBefore(LocalDateTime.now()) || activatedToken.activatedAt!!.isEqual(LocalDateTime.now()) shouldBe true
                activatedToken.token shouldBe waitingToken.token
                activatedToken.userId shouldBe waitingToken.userId
                activatedToken.position shouldBe waitingToken.position
            }
        }
        When("ACTIVE 상태의 토큰을 활성화 시도할 때") {
            Then("예외가 발생한다") {
                val activeToken = WaitingToken("test-token", 1L, 1L, TokenStatus.ACTIVE)
                val exception = shouldThrow<IllegalStateException> {
                    activeToken.activate()
                }
                exception.message?.isNotBlank() shouldBe true
            }
        }
        When("EXPIRED 상태의 토큰을 활성화 시도할 때") {
            Then("예외가 발생한다") {
                val expiredToken = WaitingToken("test-token", 1L, 1L, TokenStatus.EXPIRED)
                val exception = shouldThrow<IllegalStateException> {
                    expiredToken.activate()
                }
                exception.message?.isNotBlank() shouldBe true
            }
        }
        When("토큰을 만료시킬 때") {
            Then("EXPIRED 상태로 변경된다") {
                val waitingToken = WaitingToken("test-token", 1L, 1L, TokenStatus.WAITING)
                val expiredToken = waitingToken.expire()
                expiredToken.status shouldBe TokenStatus.EXPIRED
                expiredToken.expiredAt.shouldNotBeNull()
                expiredToken.expiredAt!!.isBefore(LocalDateTime.now().plusSeconds(1)) shouldBe true
                expiredToken.token shouldBe waitingToken.token
                expiredToken.userId shouldBe waitingToken.userId
                expiredToken.position shouldBe waitingToken.position
            }
        }
        When("ACTIVE 상태의 토큰을 만료시킬 때") {
            Then("EXPIRED 상태로 변경된다") {
                val activeToken = WaitingToken("test-token", 1L, 1L, TokenStatus.ACTIVE)
                val expiredToken = activeToken.expire()
                expiredToken.status shouldBe TokenStatus.EXPIRED
                expiredToken.expiredAt.shouldNotBeNull()
            }
        }
        When("EXPIRED 상태인 토큰의 만료 여부를 확인할 때") {
            Then("만료된 것으로 확인된다") {
                val expiredToken = WaitingToken("test-token", 1L, 1L, TokenStatus.EXPIRED)
                expiredToken.isExpired() shouldBe true
            }
        }
        When("expiredAt이 현재 시간보다 이전인 토큰의 만료 여부를 확인할 때") {
            Then("만료된 것으로 확인된다") {
                val pastTime = LocalDateTime.now().minusHours(1)
                val waitingToken = WaitingToken(
                    "test-token", 1L, 1L, TokenStatus.WAITING,
                    expiredAt = pastTime
                )
                waitingToken.isExpired() shouldBe true
            }
        }
        When("WAITING 상태이고 expiredAt이 null인 토큰의 만료 여부를 확인할 때") {
            Then("만료되지 않은 것으로 확인된다") {
                val waitingToken = WaitingToken("test-token", 1L, 1L, TokenStatus.WAITING)
                waitingToken.isExpired() shouldBe false
            }
        }
        When("ACTIVE 상태인 토큰의 만료 여부를 확인할 때") {
            Then("만료되지 않은 것으로 확인된다") {
                val activeToken = WaitingToken("test-token", 1L, 1L, TokenStatus.ACTIVE)
                activeToken.isExpired() shouldBe false
            }
        }
        When("expiredAt이 미래 시간인 토큰의 만료 여부를 확인할 때") {
            Then("만료되지 않은 것으로 확인된다") {
                val futureTime = LocalDateTime.now().plusHours(1)
                val waitingToken = WaitingToken(
                    "test-token", 1L, 1L, TokenStatus.WAITING,
                    expiredAt = futureTime
                )
                waitingToken.isExpired() shouldBe false
            }
        }
        When("copy()로 토큰의 일부 속성을 변경할 때") {
            Then("변경된 속성을 가진 복사본이 생성된다") {
                val originalToken = WaitingToken("test-token", 1L, 1L)
                val newStatus = TokenStatus.ACTIVE
                val copiedToken = originalToken.copy(status = newStatus)
                copiedToken.status shouldBe newStatus
                copiedToken.token shouldBe originalToken.token
                copiedToken.userId shouldBe originalToken.userId
                copiedToken.position shouldBe originalToken.position
            }
        }
        When("같은 속성을 가진 두 개의 토큰이 있을 때") {
            Then("동등하다고 판단된다") {
                val token1 = WaitingToken("test-token", 1L, 1L)
                val token2 = WaitingToken("test-token", 1L, 1L)
                token1 shouldBe token2
            }
        }
        When("다른 속성을 가진 두 개의 토큰이 있을 때") {
            Then("동등하지 않다고 판단된다") {
                val token1 = WaitingToken("test-token-1", 1L, 1L)
                val token2 = WaitingToken("test-token-2", 1L, 1L)
                token1 shouldNotBe token2
            }
        }
        When("issuedAt이 설정될 때") {
            Then("현재 시간과 일치하거나 이전이어야 한다") {
                val before = LocalDateTime.now()
                val token = WaitingToken("test-token", 1L, 1L)
                val after = LocalDateTime.now()
                token.issuedAt.isAfter(before) || token.issuedAt.isEqual(before) shouldBe true
                token.issuedAt.isBefore(after) || token.issuedAt.isEqual(after) shouldBe true
            }
        }
        When("다양한 Long userId 값으로 토큰을 생성할 때") {
            Then("정상적으로 생성된다") {
                val token1 = WaitingToken("token1", 1L, 1L)
                token1.userId shouldBe 1L
                val token2 = WaitingToken("token2", 999L, 2L)
                token2.userId shouldBe 999L
                val token3 = WaitingToken("token3", Long.MAX_VALUE, 3L)
                token3.userId shouldBe Long.MAX_VALUE
            }
        }
        When("음수 position으로 토큰을 생성할 때") {
            Then("정상적으로 생성된다") {
                val position = -1L
                val token = WaitingToken("test-token", 1L, position)
                token.position shouldBe -1L
            }
        }
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
