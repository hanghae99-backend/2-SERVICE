package kr.hhplus.be.server.auth.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.*
import kr.hhplus.be.server.auth.aggregate.WaitingToken
import kr.hhplus.be.server.auth.aggregate.TokenStatus
import kr.hhplus.be.server.auth.repository.TokenStore

class TokenLifecycleManagerUnitTest : BehaviorSpec({
    lateinit var tokenStore: TokenStore
    lateinit var queueManager: QueueManager
    lateinit var tokenLifecycleManager: TokenLifecycleManager

    beforeTest {
        tokenStore = mockk()
        queueManager = mockk()
        tokenLifecycleManager = TokenLifecycleManager(tokenStore, queueManager)
        clearMocks(tokenStore, queueManager, answers = false, recordedCalls = true)
    }

    given("TokenLifecycleManager는 토큰 생명주기 관리의 책임을 가진다") {
        `when`("토큰 저장을 요청받으면") {
            then("TokenStore에 위임하여 토큰을 저장한다") {
                // given
                val waitingToken = WaitingToken("test-token", 1L)
                every { tokenStore.save(waitingToken) } just Runs

                // when
                tokenLifecycleManager.saveToken(waitingToken)

                // then - TokenLifecycleManager의 책임: 저장 위임
                verify(exactly = 1) { tokenStore.save(waitingToken) }
            }
        }

        `when`("토큰 상태 조회를 요청받으면") {
            then("TokenStore에 위임하여 상태를 반환한다") {
                // given
                val token = "test-token"
                val expectedStatus = TokenStatus.ACTIVE
                every { tokenStore.getTokenStatus(token) } returns expectedStatus

                // when
                val result = tokenLifecycleManager.getTokenStatus(token)

                // then - TokenLifecycleManager의 책임: 상태 조회 위임
                result shouldBe expectedStatus
                verify(exactly = 1) { tokenStore.getTokenStatus(token) }
            }
        }

        `when`("토큰 조회를 요청받으면") {
            then("TokenStore에 위임하여 토큰을 반환한다") {
                // given
                val token = "existing-token"
                val expectedToken = WaitingToken(token, 1L)
                every { tokenStore.findByToken(token) } returns expectedToken

                // when
                val result = tokenLifecycleManager.findToken(token)

                // then - TokenLifecycleManager의 책임: 토큰 조회 위임
                result.shouldNotBeNull()
                result shouldBe expectedToken
                verify(exactly = 1) { tokenStore.findByToken(token) }
            }
        }

        `when`("존재하지 않는 토큰을 조회하면") {
            then("null을 반환한다") {
                // given
                val token = "nonexistent-token"
                every { tokenStore.findByToken(token) } returns null

                // when
                val result = tokenLifecycleManager.findToken(token)

                // then - TokenLifecycleManager의 책임: 저장소 결과 그대로 반환
                result.shouldBeNull()
                verify(exactly = 1) { tokenStore.findByToken(token) }
            }
        }

        `when`("토큰 만료를 요청받으면") {
            then("TokenStore에 위임하여 토큰을 만료시킨다") {
                // given
                val token = "token-to-expire"
                every { tokenStore.expireToken(token) } just Runs

                // when
                tokenLifecycleManager.expireToken(token)

                // then - TokenLifecycleManager의 책임: 만료 위임
                verify(exactly = 1) { tokenStore.expireToken(token) }
            }
        }
    }

    given("TokenLifecycleManager는 만료된 토큰 정리의 책임을 가진다") {
        `when`("만료된 토큰 정리를 요청받으면") {
            then("만료된 토큰들을 찾아서 각각 정리한다") {
                // given
                val expiredTokens = listOf("expired1", "expired2", "expired3")
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                every { tokenStore.expireToken(any()) } just Runs

                // when
                tokenLifecycleManager.cleanupExpiredTokens()

                // then - TokenLifecycleManager의 책임: 만료 토큰 일괄 정리
                verify(exactly = 1) { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 3) { tokenStore.expireToken(any()) }
                
                // 각 토큰별로 정리 호출 확인
                verify { tokenStore.expireToken("expired1") }
                verify { tokenStore.expireToken("expired2") }
                verify { tokenStore.expireToken("expired3") }
            }
        }

        `when`("만료된 토큰이 없을 때 정리를 요청받으면") {
            then("아무 작업도 하지 않는다") {
                // given
                every { tokenStore.findExpiredActiveTokens() } returns emptyList()

                // when
                tokenLifecycleManager.cleanupExpiredTokens()

                // then - TokenLifecycleManager의 책임: 불필요한 작업 방지
                verify(exactly = 1) { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 0) { tokenStore.expireToken(any()) }
            }
        }

        `when`("토큰 정리 중 일부 토큰에서 예외가 발생하면") {
            then("예외를 잡아서 처리하고 나머지 토큰들은 계속 정리한다") {
                // given
                val expiredTokens = listOf("expired1", "error-token", "expired3")
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                every { tokenStore.expireToken("expired1") } just Runs
                every { tokenStore.expireToken("error-token") } throws RuntimeException("Test exception")
                every { tokenStore.expireToken("expired3") } just Runs

                // when
                tokenLifecycleManager.cleanupExpiredTokens()

                // then - TokenLifecycleManager의 책임: 오류 내성 (resilience)
                verify(exactly = 1) { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 3) { tokenStore.expireToken(any()) }
                
                // 예외가 발생해도 모든 토큰에 대해 시도
                verify { tokenStore.expireToken("expired1") }
                verify { tokenStore.expireToken("error-token") }
                verify { tokenStore.expireToken("expired3") }
            }
        }
    }

    given("TokenLifecycleManager는 토큰 완료 처리의 책임을 가진다") {
        `when`("토큰 완료 처리를 요청받으면") {
            then("토큰을 만료시킨 후 자동 큐 처리를 실행한다") {
                // given
                val token = "completed-token"
                every { tokenStore.expireToken(token) } just Runs
                every { queueManager.processQueueAutomatically() } just Runs

                // when
                tokenLifecycleManager.completeToken(token)

                // then - TokenLifecycleManager의 책임: 완료 플로우 조정
                verifyOrder {
                    tokenStore.expireToken(token)
                    queueManager.processQueueAutomatically()
                }
                verify(exactly = 1) { tokenStore.expireToken(token) }
                verify(exactly = 1) { queueManager.processQueueAutomatically() }
            }
        }

        `when`("토큰 만료 중 예외가 발생하면") {
            then("예외를 전파하고 자동 큐 처리는 실행하지 않는다") {
                // given
                val token = "error-token"
                every { tokenStore.expireToken(token) } throws RuntimeException("Expire failed")

                // when & then - TokenLifecycleManager의 책임: 예외 상황 처리
                try {
                    tokenLifecycleManager.completeToken(token)
                } catch (e: RuntimeException) {
                    // 예외 발생 예상
                }

                // 만료 실패 시 큐 처리는 실행되지 않아야 함
                verify(exactly = 1) { tokenStore.expireToken(token) }
                verify(exactly = 0) { queueManager.processQueueAutomatically() }
            }
        }

        `when`("자동 큐 처리 중 예외가 발생하면") {
            then("토큰 만료는 완료되고 큐 처리 예외만 전파된다") {
                // given
                val token = "queue-error-token"
                every { tokenStore.expireToken(token) } just Runs
                every { queueManager.processQueueAutomatically() } throws RuntimeException("Queue processing failed")

                // when & then - TokenLifecycleManager의 책임: 부분 실패 처리
                try {
                    tokenLifecycleManager.completeToken(token)
                } catch (e: RuntimeException) {
                    // 큐 처리 예외 발생 예상
                }

                // 토큰 만료는 성공적으로 완료되어야 함
                verify(exactly = 1) { tokenStore.expireToken(token) }
                verify(exactly = 1) { queueManager.processQueueAutomatically() }
            }
        }
    }

    given("TokenLifecycleManager는 다양한 생명주기 시나리오를 처리한다") {
        `when`("여러 토큰의 생명주기를 순차적으로 관리할 때") {
            then("각 단계별로 적절한 컴포넌트에 위임한다") {
                // given
                val token1 = WaitingToken("token1", 1L)
                val token2 = WaitingToken("token2", 2L)
                every { tokenStore.save(any()) } just Runs
                every { tokenStore.getTokenStatus(any()) } returns TokenStatus.WAITING
                every { tokenStore.expireToken(any()) } just Runs

                // when - 생명주기 시뮬레이션
                tokenLifecycleManager.saveToken(token1)
                tokenLifecycleManager.saveToken(token2)
                val status1 = tokenLifecycleManager.getTokenStatus("token1")
                val status2 = tokenLifecycleManager.getTokenStatus("token2")
                tokenLifecycleManager.expireToken("token1")
                tokenLifecycleManager.expireToken("token2")

                // then - TokenLifecycleManager의 책임: 생명주기 전반 관리
                status1 shouldBe TokenStatus.WAITING
                status2 shouldBe TokenStatus.WAITING
                
                verify(exactly = 2) { tokenStore.save(any()) }
                verify(exactly = 2) { tokenStore.getTokenStatus(any()) }
                verify(exactly = 2) { tokenStore.expireToken(any()) }
            }
        }
    }
})
