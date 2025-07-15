package kr.hhplus.be.server.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kr.hhplus.be.server.user.User
import kr.hhplus.be.server.user.UserNotFoundException
import kr.hhplus.be.server.user.UserService

class TokenServiceUnitTest : BehaviorSpec({
    lateinit var userService: UserService
    lateinit var tokenService: TokenService

    beforeTest {
        userService = mockk()
        tokenService = TokenService(userService)
        clearMocks(userService, answers = false, recordedCalls = true)
    }

    given("TokenService 도메인") {
        `when`("존재하는 사용자에게 대기 토큰을 발급할 때") {
            then("정상적으로 토큰이 발급된다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                every { userService.findUserById(userId) } returns user

                // when
                val waitingToken = tokenService.issueWaitingToken(userId)

                // then
                waitingToken.userId shouldBe userId
                waitingToken.token.shouldNotBeBlank()
                waitingToken.position shouldBe 1L
                waitingToken.status shouldBe TokenStatus.WAITING
                waitingToken.issuedAt.shouldNotBeNull()

                verify(exactly = 1) { userService.findUserById(userId) }
            }
        }
        `when`("존재하지 않는 사용자에게 토큰 발급 시도할 때") {
            then("예외가 발생한다") {
                // given
                val userId = 999L
                every { userService.findUserById(userId) } returns null

                // when & then
                val exception = shouldThrow<UserNotFoundException> {
                    tokenService.issueWaitingToken(userId)
                }
                exception.message?.contains("사용자를 찾을 수 없습니다") shouldBe true

                verify(exactly = 1) { userService.findUserById(userId) }
            }
        }
        `when`("여러 사용자에게 토큰을 발급할 때") {
            then("각 토큰의 position이 올바르게 증가한다") {
                // given
                val userId1 = 1L
                val userId2 = 2L
                val user1 = User.create(userId1)
                val user2 = User.create(userId2)

                every { userService.findUserById(userId1) } returns user1
                every { userService.findUserById(userId2) } returns user2

                // when
                val token1 = tokenService.issueWaitingToken(userId1)
                val token2 = tokenService.issueWaitingToken(userId2)

                // then
                token1.position shouldBe 1L
                token2.position shouldBe 2L
                token1.token shouldNotBe token2.token
            }
        }
        `when`("토큰의 현재 순서를 조회할 때") {
            then("정상적으로 순서와 예상 대기 시간이 반환된다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                every { userService.findUserById(userId) } returns user

                val waitingToken = tokenService.issueWaitingToken(userId)

                // when
                val positionResponse = tokenService.getCurrentPosition(waitingToken.token)

                // then
                positionResponse.position shouldBe 1L
                (positionResponse.estimatedWaitTime >= 0L) shouldBe true
                positionResponse.status shouldBe TokenStatus.WAITING
            }
        }
        `when`("존재하지 않는 토큰으로 순서 조회 시도할 때") {
            then("예외가 발생한다") {
                // given
                val invalidToken = "invalid-token"

                // when & then
                val exception = shouldThrow<TokenNotFoundException> {
                    tokenService.getCurrentPosition(invalidToken)
                }
                exception.message?.contains("토큰을 찾을 수 없습니다") shouldBe true
            }
        }
        `when`("대기 상태인 토큰을 활성화할 때") {
            then("정상적으로 토큰이 활성화된다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                every { userService.findUserById(userId) } returns user

                val waitingToken = tokenService.issueWaitingToken(userId)

                // when
                val activatedToken = tokenService.activateUser(waitingToken.token)

                // then
                activatedToken.status shouldBe TokenStatus.ACTIVE
                activatedToken.activatedAt.shouldNotBeNull()
                activatedToken.token shouldBe waitingToken.token
                activatedToken.userId shouldBe userId
            }
        }
        `when`("존재하지 않는 토큰을 활성화 시도할 때") {
            then("예외가 발생한다") {
                // given
                val invalidToken = "invalid-token"

                // when & then
                val exception = shouldThrow<TokenNotFoundException> {
                    tokenService.activateUser(invalidToken)
                }
                exception.message?.contains("토큰을 찾을 수 없습니다") shouldBe true
            }
        }
        `when`("이미 활성화된 토큰을 다시 활성화하려고 할 때") {
            then("예외가 발생한다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                every { userService.findUserById(userId) } returns user

                val waitingToken = tokenService.issueWaitingToken(userId)
                tokenService.activateUser(waitingToken.token)

                // when & then
                val exception = shouldThrow<TokenActivationException> {
                    tokenService.activateUser(waitingToken.token)
                }
                exception.message?.contains("토큰을 활성화할 수 없습니다") shouldBe true
            }
        }
        `when`("활성 토큰이 100개일 때 추가 활성화 시도할 때") {
            then("예외가 발생한다") {
                // given - 100개의 활성 토큰 생성
                repeat(100) { i ->
                    val userId = i.toLong() + 1
                    val user = User.create(userId)
                    every { userService.findUserById(userId) } returns user

                    val token = tokenService.issueWaitingToken(userId)
                    tokenService.activateUser(token.token)
                }

                // 101번째 사용자
                val userId101 = 101L
                val user101 = User.create(userId101)
                every { userService.findUserById(userId101) } returns user101
                val waitingToken101 = tokenService.issueWaitingToken(userId101)

                // when & then
                val exception = shouldThrow<TokenActivationException> {
                    tokenService.activateUser(waitingToken101.token)
                }
                exception.message?.contains("현재 활성화할 수 없습니다") shouldBe true
            }
        }
        `when`("토큰을 만료시킬 때") {
            then("정상적으로 토큰이 만료된다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                every { userService.findUserById(userId) } returns user

                val waitingToken = tokenService.issueWaitingToken(userId)

                // when
                val expiredToken = tokenService.expireToken(waitingToken.token)

                // then
                expiredToken.status shouldBe TokenStatus.EXPIRED
                expiredToken.expiredAt.shouldNotBeNull()
                expiredToken.token shouldBe waitingToken.token
            }
        }
        `when`("존재하지 않는 토큰 만료 시도할 때") {
            then("예외가 발생한다") {
                // given
                val invalidToken = "invalid-token"

                // when & then
                val exception = shouldThrow<TokenNotFoundException> {
                    tokenService.expireToken(invalidToken)
                }
                exception.message?.contains("토큰을 찾을 수 없습니다") shouldBe true
            }
        }
        `when`("사용자의 모든 토큰을 조회할 때") {
            then("정상적으로 토큰 리스트가 반환된다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                every { userService.findUserById(userId) } returns user

                val token1 = tokenService.issueWaitingToken(userId)
                val token2 = tokenService.issueWaitingToken(userId)

                // when
                val userTokens = tokenService.getUserTokens(userId)

                // then
                userTokens.size shouldBe 2
                userTokens.map { it.token } shouldContainExactly listOf(token1.token, token2.token)
                userTokens.all { it.userId == userId } shouldBe true
            }
        }
        `when`("토큰이 없는 사용자의 토큰 조회 시도할 때") {
            then("빈 리스트가 반환된다") {
                // given
                val userId = 999L

                // when
                val userTokens = tokenService.getUserTokens(userId)

                // then
                userTokens.shouldBeEmpty()
            }
        }
        `when`("예상 대기 시간을 계산할 때") {
            then("정상적으로 예상 대기 시간이 계산된다") {
                // given
                val userId1 = 1L
                val userId2 = 2L
                val userId3 = 3L

                val user1 = User.create(userId1)
                val user2 = User.create(userId2)
                val user3 = User.create(userId3)

                every { userService.findUserById(userId1) } returns user1
                every { userService.findUserById(userId2) } returns user2
                every { userService.findUserById(userId3) } returns user3

                val token1 = tokenService.issueWaitingToken(userId1) // position: 1
                val token2 = tokenService.issueWaitingToken(userId2) // position: 2
                val token3 = tokenService.issueWaitingToken(userId3) // position: 3

                // 첫 번째 토큰 활성화
                tokenService.activateUser(token1.token)

                // when
                val position2 = tokenService.getCurrentPosition(token2.token)
                val position3 = tokenService.getCurrentPosition(token3.token)

                // then
                // position 2: 활성 토큰 1개, 대기 위치 = 2 - 1 = 1, 예상 시간 = 1 * 30 = 30초
                position2.estimatedWaitTime shouldBe 30L

                // position 3: 활성 토큰 1개, 대기 위치 = 3 - 1 = 2, 예상 시간 = 2 * 30 = 60초
                position3.estimatedWaitTime shouldBe 60L
            }
        }
        `when`("활성화된 토큰의 예상 대기 시간을 조회할 때") {
            then("0이 반환된다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                every { userService.findUserById(userId) } returns user

                val token = tokenService.issueWaitingToken(userId)
                tokenService.activateUser(token.token)

                // when
                val position = tokenService.getCurrentPosition(token.token)

                // then
                position.estimatedWaitTime shouldBe 0L
            }
        }
        `when`("토큰 생성 시") {
            then("고유한 토큰 문자열이 생성된다") {
                // given
                val userId1 = 1L
                val userId2 = 2L
                val user1 = User.create(userId1)
                val user2 = User.create(userId2)

                every { userService.findUserById(userId1) } returns user1
                every { userService.findUserById(userId2) } returns user2

                // when
                val token1 = tokenService.issueWaitingToken(userId1)
                val token2 = tokenService.issueWaitingToken(userId2)

                // then
                token1.token shouldNotBe token2.token
                token1.token.shouldNotBeBlank()
                token2.token.shouldNotBeBlank()
            }
        }
        `when`("동시성 테스트 - 여러 토큰의 position이 올바르게 증가할 때") {
            then("각 토큰의 position이 1부터 N까지 순서대로 부여된다") {
                // given
                val userIds = (1L..10L).toList()
                userIds.forEach { userId ->
                    val user = User.create(userId)
                    every { userService.findUserById(userId) } returns user
                }

                // when
                val tokens = userIds.map { userId ->
                    tokenService.issueWaitingToken(userId)
                }

                // then
                val positions = tokens.map { it.position }.sorted()
                positions shouldContainExactly listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            }
        }
    }

    // ============= 도메인 객체 테스트 =============

    given("도메인 객체") {
        `when`("TokenPositionResponse를 생성할 때") {
            then("정상적으로 생성된다") {
                // given
                val position = 5L
                val estimatedWaitTime = 150L
                val status = TokenStatus.WAITING

                // when
                val response = TokenPositionResponse(position, estimatedWaitTime, status)

                // then
                response.position shouldBe position
                response.estimatedWaitTime shouldBe estimatedWaitTime
                response.status shouldBe status
            }
        }
        `when`("TokenNotFoundException을 생성할 때") {
            then("올바른 메시지를 가진다") {
                // given
                val message = "토큰을 찾을 수 없습니다"

                // when
                val exception = TokenNotFoundException(message)

                // then
                exception.shouldBeInstanceOf<RuntimeException>()
                exception.message shouldBe message
            }
        }
        `when`("TokenActivationException을 생성할 때") {
            then("올바른 메시지를 가진다") {
                // given
                val message = "토큰을 활성화할 수 없습니다"

                // when
                val exception = TokenActivationException(message)

                // then
                exception.shouldBeInstanceOf<RuntimeException>()
                exception.message shouldBe message
            }
        }
    }
})
