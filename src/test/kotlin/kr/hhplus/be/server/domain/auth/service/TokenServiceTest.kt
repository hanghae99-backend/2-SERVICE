package kr.hhplus.be.server.domain.auth.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException

class TokenServiceTest : DescribeSpec({
    
    val userService = mockk<UserService>()
    val tokenFactory = mockk<TokenFactory>()
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()
    val queueManager = mockk<QueueManager>()
    
    val tokenService = TokenService(
        userService,
        tokenFactory,
        tokenLifecycleManager,
        queueManager
    )
    
    describe("issueWaitingToken") {
        context("유효한 사용자가 토큰을 발급받을 때") {
            it("대기 토큰을 발급하고 상세 정보를 반환해야 한다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                val waitingToken = mockk<WaitingToken> {
                    every { token } returns "test-token"
                }
                val queuePosition = 4 // 0-based
                
                every { userService.getUserById(userId) } returns user
                every { tokenFactory.createWaitingToken(userId) } returns waitingToken
                every { tokenLifecycleManager.saveToken(waitingToken) } returns Unit
                every { queueManager.addToQueue("test-token") } returns Unit
                every { queueManager.getQueuePosition("test-token") } returns queuePosition
                
                // when
                val result = tokenService.issueWaitingToken(userId)
                
                // then
                result shouldNotBe null
                result.token shouldBe "test-token"
                result.status shouldBe "WAITING"
                result.userId shouldBe userId
                result.queuePosition shouldBe queuePosition + 1 // 1부터 시작
                result.estimatedWaitingTime shouldBe (queuePosition + 1) * 2 // 대기순서 * 2분
                result.message shouldBe "대기열에 등록되었습니다"
                
                verify { userService.getUserById(userId) }
                verify { tokenFactory.createWaitingToken(userId) }
                verify { tokenLifecycleManager.saveToken(waitingToken) }
                verify { queueManager.addToQueue("test-token") }
                verify { queueManager.getQueuePosition("test-token") }
            }
        }
        
        context("존재하지 않는 사용자가 토큰을 발급받을 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                
                every { userService.getUserById(userId) } returns null
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    tokenService.issueWaitingToken(userId)
                }
                
                verify { userService.getUserById(userId) }
            }
        }
    }
    
    describe("getTokenQueueStatus") {
        context("대기 중인 토큰의 상태를 조회할 때") {
            it("대기 순서와 예상 시간이 포함된 상태를 반환해야 한다") {
                // given
                val token = "test-token"
                val waitingToken = mockk<WaitingToken>()
                val queuePosition = 3
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING
                every { queueManager.getQueuePosition(token) } returns queuePosition
                
                // when
                val result = tokenService.getTokenQueueStatus(token)
                
                // then
                result shouldNotBe null
                result.token shouldBe token
                result.status shouldBe "WAITING"
                result.message shouldBe "대기 중입니다"
                result.queuePosition shouldBe queuePosition + 1
                result.estimatedWaitingTime shouldBe (queuePosition + 1) * 2
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { queueManager.getQueuePosition(token) }
            }
        }
        
        context("활성화된 토큰의 상태를 조회할 때") {
            it("서비스 이용 가능 상태를 반환해야 한다") {
                // given
                val token = "active-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                
                // when
                val result = tokenService.getTokenQueueStatus(token)
                
                // then
                result shouldNotBe null
                result.token shouldBe token
                result.status shouldBe "ACTIVE"
                result.message shouldBe "서비스 이용 가능합니다"
                result.queuePosition shouldBe null
                result.estimatedWaitingTime shouldBe null
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
            }
        }
        
        context("존재하지 않는 토큰의 상태를 조회할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    tokenService.getTokenQueueStatus(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
            }
        }
    }
    
    describe("validateActiveToken") {
        context("활성화된 토큰을 검증할 때") {
            it("토큰 객체를 반환해야 한다") {
                // given
                val token = "active-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                
                // when
                val result = tokenService.validateActiveToken(token)
                
                // then
                result shouldBe waitingToken
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
            }
        }
        
        context("대기 중인 토큰을 검증할 때") {
            it("TokenActivationException을 던져야 한다") {
                // given
                val token = "waiting-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING
                
                // when & then
                shouldThrow<TokenActivationException> {
                    tokenService.validateActiveToken(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
            }
        }
        
        context("존재하지 않는 토큰을 검증할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    tokenService.validateActiveToken(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
            }
        }
    }
    
    describe("completeReservation") {
        context("유효한 토큰으로 예약 완료할 때") {
            it("토큰을 완료 처리해야 한다") {
                // given
                val token = "valid-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.completeToken(token) } returns Unit
                
                // when
                tokenService.completeReservation(token)
                
                // then
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.completeToken(token) }
            }
        }
        
        context("존재하지 않는 토큰으로 예약 완료할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    tokenService.completeReservation(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
            }
        }
    }
    
    describe("processQueueAutomatically") {
        context("자동 큐 처리를 실행할 때") {
            it("만료된 토큰을 정리하고 대기열을 처리해야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokens() } returns Unit
                every { queueManager.processQueueAutomatically() } returns Unit
                
                // when
                tokenService.processQueueAutomatically()
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokens() }
                verify { queueManager.processQueueAutomatically() }
            }
        }
    }
})