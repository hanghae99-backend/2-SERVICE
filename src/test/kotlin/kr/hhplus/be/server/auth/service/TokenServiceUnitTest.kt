package kr.hhplus.be.server.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import kr.hhplus.be.server.auth.entity.TokenStatus
import kr.hhplus.be.server.auth.entity.WaitingToken
import kr.hhplus.be.server.auth.factory.TokenFactory
import kr.hhplus.be.server.auth.service.QueueManager
import kr.hhplus.be.server.auth.service.QueueStatusResponse
import kr.hhplus.be.server.auth.service.TokenActivationException
import kr.hhplus.be.server.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.auth.service.TokenNotFoundException
import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.auth.service.UserValidator
import kr.hhplus.be.server.user.entity.UserNotFoundException

class TokenServiceUnitTest : BehaviorSpec({
    lateinit var userValidator: UserValidator
    lateinit var tokenFactory: TokenFactory
    lateinit var tokenLifecycleManager: TokenLifecycleManager
    lateinit var queueManager: QueueManager
    lateinit var tokenService: TokenService

    beforeTest {
        userValidator = mockk()
        tokenFactory = mockk()
        tokenLifecycleManager = mockk()
        queueManager = mockk()
        tokenService = TokenService(userValidator, tokenFactory, tokenLifecycleManager, queueManager)
        clearMocks(userValidator, tokenFactory, tokenLifecycleManager, queueManager, answers = false, recordedCalls = true)
    }

    given("TokenService는 비즈니스 플로우 조정의 책임을 가진다") {
        `when`("대기 토큰 발급 플로우를 요청받으면") {
            then("각 전문 컴포넌트들을 순서대로 조정하여 완전한 발급 플로우를 실행한다") {
                // given
                val userId = 1L
                val expectedToken = WaitingToken("generated-token", userId)
                
                every { userValidator.validateTokenIssuable(userId) } just Runs
                every { tokenFactory.createWaitingToken(userId) } returns expectedToken
                every { tokenLifecycleManager.saveToken(expectedToken) } just Runs
                every { queueManager.addToQueue(expectedToken.token) } just Runs

                // when
                val result = tokenService.issueWaitingToken(userId)

                // then - TokenService의 책임: 비즈니스 플로우 조정
                result shouldBe expectedToken
                
                // 플로우 순서 검증: 1. 검증 -> 2. 생성 -> 3. 저장 -> 4. 큐 추가
                verifyOrder {
                    userValidator.validateTokenIssuable(userId)
                    tokenFactory.createWaitingToken(userId)
                    tokenLifecycleManager.saveToken(expectedToken)
                    queueManager.addToQueue(expectedToken.token)
                }
            }
        }

        `when`("사용자 검증 실패 시") {
            then("비즈니스 플로우를 중단하고 예외를 전파한다") {
                // given
                val userId = 999L
                every { userValidator.validateTokenIssuable(userId) } throws UserNotFoundException("사용자 없음")

                // when & then - TokenService의 책임: 예외 전파 및 플로우 중단
                shouldThrow<UserNotFoundException> {
                    tokenService.issueWaitingToken(userId)
                }

                // 검증 실패 후 후속 작업이 실행되지 않았는지 확인
                verify(exactly = 1) { userValidator.validateTokenIssuable(userId) }
                verify(exactly = 0) { tokenFactory.createWaitingToken(any()) }
                verify(exactly = 0) { tokenLifecycleManager.saveToken(any()) }
                verify(exactly = 0) { queueManager.addToQueue(any()) }
            }
        }

        `when`("토큰 상태 조회 플로우를 요청받으면") {
            then("토큰 존재 확인 후 상태를 조회하고 사용자 친화적 메시지로 변환한다") {
                // given
                val token = "test-token"
                val waitingToken = WaitingToken(token, 1L)
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING

                // when
                val result = tokenService.getTokenStatus(token)

                // then - TokenService의 책임: 조회 플로우 조정 + 메시지 변환
                result.status shouldBe TokenStatus.WAITING
                result.message shouldBe "대기 중입니다"

                verifyOrder {
                    tokenLifecycleManager.findToken(token)
                    tokenLifecycleManager.getTokenStatus(token)
                }
            }
        }

        `when`("존재하지 않는 토큰 조회 시") {
            then("비즈니스 규칙에 따라 예외를 발생시킨다") {
                // given
                val token = "nonexistent-token"
                every { tokenLifecycleManager.findToken(token) } returns null

                // when & then - TokenService의 책임: 비즈니스 규칙 적용
                val exception = shouldThrow<TokenNotFoundException> {
                    tokenService.getTokenStatus(token)
                }

                exception.message?.contains("토큰을 찾을 수 없습니다") shouldBe true
                verify(exactly = 1) { tokenLifecycleManager.findToken(token) }
                verify(exactly = 0) { tokenLifecycleManager.getTokenStatus(any()) }
            }
        }

        `when`("활성 토큰 검증 플로우를 요청받으면") {
            then("토큰 존재 확인 후 ACTIVE 상태만 허용하는 비즈니스 규칙을 적용한다") {
                // given
                val token = "active-token"
                val waitingToken = WaitingToken(token, 1L)
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE

                // when
                val result = tokenService.validateActiveToken(token)

                // then - TokenService의 책임: 검증 플로우 조정 + 비즈니스 규칙 적용
                result shouldBe waitingToken

                verifyOrder {
                    tokenLifecycleManager.findToken(token)
                    tokenLifecycleManager.getTokenStatus(token)
                }
            }
        }

        `when`("비활성 토큰 검증 시") {
            then("비즈니스 규칙에 따라 예외를 발생시킨다") {
                // given
                val token = "waiting-token"
                val waitingToken = WaitingToken(token, 1L)
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING

                // when & then - TokenService의 책임: 비즈니스 규칙 적용 (ACTIVE만 허용)
                val exception = shouldThrow<TokenActivationException> {
                    tokenService.validateActiveToken(token)
                }

                exception.message?.contains("활성화된 토큰이 아닙니다") shouldBe true
            }
        }

        `when`("예약 완료 플로우를 요청받으면") {
            then("토큰 존재 확인 후 완료 처리를 TokenLifecycleManager에 위임한다") {
                // given
                val token = "completed-token"
                val waitingToken = WaitingToken(token, 1L)
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.completeToken(token) } just Runs

                // when
                tokenService.completeReservation(token)

                // then - TokenService의 책임: 완료 플로우 조정
                verifyOrder {
                    tokenLifecycleManager.findToken(token)
                    tokenLifecycleManager.completeToken(token)
                }
            }
        }

        `when`("자동 큐 처리 플로우를 요청받으면") {
            then("만료 토큰 정리 후 대기열 자동 처리를 순서대로 실행한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokens() } just Runs
                every { queueManager.processQueueAutomatically() } just Runs

                // when
                tokenService.processQueueAutomatically()

                // then - TokenService의 책임: 자동 처리 플로우 조정
                verifyOrder {
                    tokenLifecycleManager.cleanupExpiredTokens()
                    queueManager.processQueueAutomatically()
                }
            }
        }

        `when`("큐 상태 조회를 요청받으면") {
            then("QueueManager에 위임하여 상태를 반환한다") {
                // given
                val expectedStatus = QueueStatusResponse(100L, 85L, 100L, 15L)
                every { queueManager.getQueueStatus() } returns expectedStatus

                // when
                val result = tokenService.getQueueStatus()

                // then - TokenService의 책임: 조회 위임
                result shouldBe expectedStatus
                verify(exactly = 1) { queueManager.getQueueStatus() }
            }
        }
    }

    given("TokenService는 각 상태별 메시지 변환의 책임을 가진다") {
        `when`("다양한 토큰 상태를 조회하면") {
            then("각 상태에 맞는 사용자 친화적 메시지로 변환한다") {
                // given
                val token = "test-token"
                val waitingToken = WaitingToken(token, 1L)
                every { tokenLifecycleManager.findToken(token) } returns waitingToken

                // WAITING 상태 테스트
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING
                val waitingResponse = tokenService.getTokenStatus(token)
                waitingResponse.message shouldBe "대기 중입니다"

                // ACTIVE 상태 테스트
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                val activeResponse = tokenService.getTokenStatus(token)
                activeResponse.message shouldBe "예약 가능합니다"

                // EXPIRED 상태 테스트
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.EXPIRED
                val expiredResponse = tokenService.getTokenStatus(token)
                expiredResponse.message shouldBe "토큰이 만료되었습니다"
            }
        }
    }
})
