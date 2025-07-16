package kr.hhplus.be.server.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kr.hhplus.be.server.user.User
import kr.hhplus.be.server.user.UserNotFoundException
import kr.hhplus.be.server.user.UserService
import kr.hhplus.be.server.auth.store.TokenStore

class TokenServiceUnitTest : BehaviorSpec({
    lateinit var userService: UserService
    lateinit var tokenStore: TokenStore
    lateinit var tokenService: TokenService

    beforeTest {
        userService = mockk()
        tokenStore = mockk()
        tokenService = TokenService(userService, tokenStore)
        clearMocks(userService, tokenStore, answers = false, recordedCalls = true)
    }

    given("TokenService - 콘서트 예약 토큰 발급") {
        `when`("존재하는 사용자에게 대기 토큰을 발급할 때") {
            then("정상적으로 토큰이 발급되고 큐에 추가된다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                every { userService.findUserById(userId) } returns user
                every { tokenStore.save(any()) } just Runs
                every { tokenStore.addToWaitingQueue(any()) } just Runs

                // when
                val waitingToken = tokenService.issueWaitingToken(userId)

                // then
                waitingToken.userId shouldBe userId
                waitingToken.token.shouldNotBeBlank()

                verify(exactly = 1) { userService.findUserById(userId) }
                verify(exactly = 1) { tokenStore.save(any()) }
                verify(exactly = 1) { tokenStore.addToWaitingQueue(any()) }
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
                verify(exactly = 0) { tokenStore.save(any()) }
                verify(exactly = 0) { tokenStore.addToWaitingQueue(any()) }
            }
        }
    }

    given("TokenService - 콘서트 예약 토큰 상태 조회") {
        `when`("WAITING 토큰의 상태를 조회할 때") {
            then("대기 메시지와 함께 상태가 반환된다") {
                // given
                val userId = 1L
                val token = "waiting-token"
                val waitingToken = WaitingToken(token, userId)
                
                every { tokenStore.findByToken(token) } returns waitingToken
                every { tokenStore.getTokenStatus(token) } returns TokenStatus.WAITING

                // when
                val response = tokenService.getTokenStatus(token)

                // then
                response.status shouldBe TokenStatus.WAITING
                response.message shouldBe "대기 중입니다"

                verify(exactly = 1) { tokenStore.findByToken(token) }
                verify(exactly = 1) { tokenStore.getTokenStatus(token) }
            }
        }
        `when`("ACTIVE 토큰의 상태를 조회할 때") {
            then("예약 가능 메시지와 함께 상태가 반환된다") {
                // given
                val userId = 1L
                val token = "active-token"
                val waitingToken = WaitingToken(token, userId)
                
                every { tokenStore.findByToken(token) } returns waitingToken
                every { tokenStore.getTokenStatus(token) } returns TokenStatus.ACTIVE

                // when
                val response = tokenService.getTokenStatus(token)

                // then
                response.status shouldBe TokenStatus.ACTIVE
                response.message shouldBe "예약 가능합니다"

                verify(exactly = 1) { tokenStore.findByToken(token) }
                verify(exactly = 1) { tokenStore.getTokenStatus(token) }
            }
        }
        `when`("EXPIRED 토큰의 상태를 조회할 때") {
            then("만료 메시지와 함께 상태가 반환된다") {
                // given
                val userId = 1L
                val token = "expired-token"
                val waitingToken = WaitingToken(token, userId)
                
                every { tokenStore.findByToken(token) } returns waitingToken
                every { tokenStore.getTokenStatus(token) } returns TokenStatus.EXPIRED

                // when
                val response = tokenService.getTokenStatus(token)

                // then
                response.status shouldBe TokenStatus.EXPIRED
                response.message shouldBe "토큰이 만료되었습니다"

                verify(exactly = 1) { tokenStore.findByToken(token) }
                verify(exactly = 1) { tokenStore.getTokenStatus(token) }
            }
        }
    }

    given("TokenService - 콘서트 예약 토큰 검증") {
        `when`("활성화된 토큰을 검증할 때") {
            then("정상적으로 토큰이 반환된다") {
                // given
                val userId = 1L
                val token = "active-token"
                val waitingToken = WaitingToken(token, userId)
                
                every { tokenStore.findByToken(token) } returns waitingToken
                every { tokenStore.getTokenStatus(token) } returns TokenStatus.ACTIVE

                // when
                val result = tokenService.validateActiveToken(token)

                // then
                result.token shouldBe token
                result.userId shouldBe userId

                verify(exactly = 1) { tokenStore.findByToken(token) }
                verify(exactly = 1) { tokenStore.getTokenStatus(token) }
            }
        }
        `when`("WAITING 상태 토큰을 검증할 때") {
            then("예외가 발생한다") {
                // given
                val userId = 1L
                val token = "waiting-token"
                val waitingToken = WaitingToken(token, userId)
                
                every { tokenStore.findByToken(token) } returns waitingToken
                every { tokenStore.getTokenStatus(token) } returns TokenStatus.WAITING

                // when & then
                val exception = shouldThrow<TokenActivationException> {
                    tokenService.validateActiveToken(token)
                }
                exception.message?.contains("활성화된 토큰이 아닙니다") shouldBe true
                
                verify(exactly = 1) { tokenStore.findByToken(token) }
                verify(exactly = 1) { tokenStore.getTokenStatus(token) }
            }
        }
    }

    given("TokenService - 콘서트 예약 완료 처리") {
        `when`("예약/결제 완료로 토큰을 만료시킬 때") {
            then("토큰이 만료되고 다음 사용자가 자동 활성화된다") {
                // given
                val userId = 1L
                val token = "reservation-complete-token"
                val waitingToken = WaitingToken(token, userId)
                val nextTokens = listOf("next-token1", "next-token2")
                val nextToken1 = WaitingToken("next-token1", 2L)
                val nextToken2 = WaitingToken("next-token2", 3L)
                
                every { tokenStore.findByToken(token) } returns waitingToken
                every { tokenStore.expireToken(token) } just Runs
                every { tokenStore.countActiveTokens() } returns 98L
                every { tokenStore.getNextTokensFromQueue(2) } returns nextTokens
                every { tokenStore.findByToken("next-token1") } returns nextToken1
                every { tokenStore.findByToken("next-token2") } returns nextToken2
                every { tokenStore.activateToken(any()) } just Runs

                // when
                tokenService.completeReservation(token)

                // then
                verify(exactly = 1) { tokenStore.findByToken(token) }
                verify(exactly = 1) { tokenStore.expireToken(token) }
                verify(exactly = 1) { tokenStore.countActiveTokens() }
                verify(exactly = 1) { tokenStore.getNextTokensFromQueue(2) }
                verify(exactly = 2) { tokenStore.activateToken(any()) }
            }
        }
    }

    given("TokenService - 콘서트 예약 자동 큐 처리") {
        `when`("스케줄러가 자동 큐 처리를 실행할 때") {
            then("만료된 토큰 정리 후 가용 슬롯을 채운다") {
                // given
                val expiredTokens = listOf("expired1", "expired2")
                val activeCount = 95L
                val availableSlots = 5
                val nextTokens = listOf("token1", "token2", "token3")
                
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                every { tokenStore.expireToken(any()) } just Runs
                every { tokenStore.countActiveTokens() } returns activeCount
                every { tokenStore.getNextTokensFromQueue(availableSlots) } returns nextTokens
                
                nextTokens.forEach { tokenString ->
                    val waitingToken = WaitingToken(tokenString, 1L)
                    every { tokenStore.findByToken(tokenString) } returns waitingToken
                }
                every { tokenStore.activateToken(any()) } just Runs

                // when
                tokenService.processQueueAutomatically()

                // then
                verify(exactly = 1) { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 2) { tokenStore.expireToken(any()) } // expired1, expired2
                verify(exactly = 1) { tokenStore.countActiveTokens() }
                verify(exactly = 1) { tokenStore.getNextTokensFromQueue(availableSlots) }
                verify(exactly = 3) { tokenStore.activateToken(any()) } // token1, token2, token3
            }
        }
        `when`("만료된 활성 토큰들을 정리할 때") {
            then("만료된 토큰들이 정리된다") {
                // given
                val expiredTokens = listOf("expired1", "expired2", "expired3")
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                every { tokenStore.expireToken(any()) } just Runs

                // when
                tokenService.cleanupExpiredActiveTokens()

                // then
                verify(exactly = 1) { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 3) { tokenStore.expireToken(any()) }
            }
        }
    }

    given("TokenService - 콘서트 예약 큐 상태 조회") {
        `when`("큐 상태를 조회할 때") {
            then("정상적으로 큐 정보가 반환된다") {
                // given
                val queueSize = 1500L // 대기자 1500명
                val activeCount = 95L  // 활성 사용자 95명
                val maxActiveTokens = 100L
                val expectedAvailableSlots = maxActiveTokens - activeCount
                
                every { tokenStore.getQueueSize() } returns queueSize
                every { tokenStore.countActiveTokens() } returns activeCount

                // when
                val queueStatus = tokenService.getQueueStatus()

                // then
                queueStatus.queueSize shouldBe queueSize
                queueStatus.activeTokens shouldBe activeCount
                queueStatus.maxActiveTokens shouldBe maxActiveTokens
                queueStatus.availableSlots shouldBe expectedAvailableSlots
                
                verify(exactly = 1) { tokenStore.getQueueSize() }
                verify(exactly = 1) { tokenStore.countActiveTokens() }
            }
        }
    }

    // ============= 간소화된 도메인 객체 테스트 =============

    given("간소화된 도메인 객체") {
        `when`("TokenStatusResponse를 생성할 때") {
            then("정상적으로 생성된다") {
                // given
                val status = TokenStatus.WAITING
                val message = "대기 중입니다"

                // when
                val response = TokenStatusResponse(status, message)

                // then
                response.status shouldBe status
                response.message shouldBe message
            }
        }
        `when`("QueueStatusResponse를 생성할 때") {
            then("콘서트 예약 서비스 상황이 반영된다") {
                // given
                val queueSize = 5000L // 대기자 5000명
                val activeTokens = 100L // 활성 사용자 100명 (최대치)
                val maxActiveTokens = 100L
                val availableSlots = 0L // 가용 슬롯 없음

                // when
                val response = QueueStatusResponse(queueSize, activeTokens, maxActiveTokens, availableSlots)

                // then
                response.queueSize shouldBe queueSize
                response.activeTokens shouldBe activeTokens
                response.maxActiveTokens shouldBe maxActiveTokens
                response.availableSlots shouldBe availableSlots
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
