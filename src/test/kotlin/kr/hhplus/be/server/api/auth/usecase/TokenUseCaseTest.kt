package kr.hhplus.be.server.api.auth.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.auth.usecase.TokenUseCase
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.user.service.UserService

class TokenUseCaseTest : DescribeSpec({
    
    val tokenDomainService = mockk<TokenDomainService>()
    val userService = mockk<UserService>()
    val tokenFactory = mockk<TokenFactory>()
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()
    val queueManager = mockk<QueueManager>()
    
    val tokenUseCase = TokenUseCase(
        tokenDomainService,
        userService,
        tokenFactory,
        tokenLifecycleManager,
        queueManager
    )
    
    describe("issueWaitingToken") {
        context("유효한 사용자가 토큰을 발급받을 때") {
            it("대기 토큰을 발급하고 대기열에 추가해야 한다") {
                // given
                val userId = 1L
                val user = User.create(userId)
                val waitingToken = mockk<WaitingToken> {
                    every { token } returns "test-token"
                }
                val queuePosition = 4 // 0-based index
                val estimatedWaitingTime = 10
                
                every { userService.getUserById(userId) } returns user
                every { tokenFactory.createWaitingToken(userId) } returns waitingToken
                every { tokenLifecycleManager.saveToken(waitingToken) } returns Unit
                every { queueManager.addToQueue("test-token") } returns Unit
                every { queueManager.getQueuePosition("test-token") } returns queuePosition
                every { tokenDomainService.calculateWaitingTime(queuePosition + 1) } returns estimatedWaitingTime
                
                // when
                val result = tokenUseCase.issueWaitingToken(userId)
                
                // then
                result shouldNotBe null
                result.token shouldBe "test-token"
                result.status shouldBe "WAITING"
                result.queuePosition shouldBe queuePosition + 1
                result.estimatedWaitingTime shouldBe estimatedWaitingTime
                result.message shouldBe "대기열에 등록되었습니다"
                
                verify { userService.getUserById(userId) }
                verify { tokenFactory.createWaitingToken(userId) }
                verify { tokenLifecycleManager.saveToken(waitingToken) }
                verify { queueManager.addToQueue("test-token") }
                verify { queueManager.getQueuePosition("test-token") }
                verify { tokenDomainService.calculateWaitingTime(queuePosition + 1) }
            }
        }
        
        context("존재하지 않는 사용자가 토큰을 발급받을 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                
                every { userService.getUserById(userId) } returns null

                // when & then
                shouldThrow<UserNotFoundException> {
                    tokenUseCase.issueWaitingToken(userId)
                }
                
                verify { userService.getUserById(userId) }
            }
        }
    }
    
    describe("getTokenQueueStatus") {
        context("대기 중인 토큰의 상태를 조회할 때") {
            it("대기 순서와 예상 대기 시간을 포함한 상태를 반환해야 한다") {
                // given
                val token = "test-token"
                val waitingToken = mockk<WaitingToken>()
                val queuePosition = 3
                val estimatedWaitingTime = 8
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING
                every { queueManager.getQueuePosition(token) } returns queuePosition
                every { tokenDomainService.calculateWaitingTime(queuePosition + 1) } returns estimatedWaitingTime
                every { tokenDomainService.getStatusMessage(TokenStatus.WAITING) } returns "대기 중입니다"
                
                // when
                val result = tokenUseCase.getTokenQueueStatus(token)
                
                // then
                result shouldNotBe null
                result.token shouldBe token
                result.status shouldBe "WAITING"
                result.message shouldBe "대기 중입니다"
                result.queuePosition shouldBe queuePosition + 1
                result.estimatedWaitingTime shouldBe estimatedWaitingTime
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { queueManager.getQueuePosition(token) }
                verify { tokenDomainService.calculateWaitingTime(queuePosition + 1) }
                verify { tokenDomainService.getStatusMessage(TokenStatus.WAITING) }
            }
        }
        
        context("활성화된 토큰의 상태를 조회할 때") {
            it("서비스 이용 가능 상태를 반환해야 한다") {
                // given
                val token = "test-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenDomainService.getStatusMessage(TokenStatus.ACTIVE) } returns "서비스 이용 가능합니다"
                
                // when
                val result = tokenUseCase.getTokenQueueStatus(token)
                
                // then
                result shouldNotBe null
                result.token shouldBe token
                result.status shouldBe "ACTIVE"
                result.message shouldBe "서비스 이용 가능합니다"
                result.queuePosition shouldBe null
                result.estimatedWaitingTime shouldBe null
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.getStatusMessage(TokenStatus.ACTIVE) }
            }
        }
        
        context("만료된 토큰의 상태를 조회할 때") {
            it("만료 상태를 반환해야 한다") {
                // given
                val token = "test-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.EXPIRED
                every { tokenDomainService.getStatusMessage(TokenStatus.EXPIRED) } returns "토큰이 만료되었습니다"
                
                // when
                val result = tokenUseCase.getTokenQueueStatus(token)
                
                // then
                result shouldNotBe null
                result.token shouldBe token
                result.status shouldBe "EXPIRED"
                result.message shouldBe "토큰이 만료되었습니다"
                result.queuePosition shouldBe null
                result.estimatedWaitingTime shouldBe null
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.getStatusMessage(TokenStatus.EXPIRED) }
            }
        }
        
        context("존재하지 않는 토큰의 상태를 조회할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    tokenUseCase.getTokenQueueStatus(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
            }
        }
    }
    
    describe("getSimpleTokenStatus") {
        context("토큰의 간단한 상태를 조회할 때") {
            it("토큰, 상태, 메시지만 포함한 정보를 반환해야 한다") {
                // given
                val token = "test-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenDomainService.getStatusMessage(TokenStatus.ACTIVE) } returns "서비스 이용 가능합니다"
                
                // when
                val result = tokenUseCase.getSimpleTokenStatus(token)
                
                // then
                result shouldNotBe null
                result.token shouldBe token
                result.status shouldBe "ACTIVE"
                result.message shouldBe "서비스 이용 가능합니다"
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.getStatusMessage(TokenStatus.ACTIVE) }
            }
        }
        
        context("존재하지 않는 토큰의 간단한 상태를 조회할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    tokenUseCase.getSimpleTokenStatus(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
            }
        }
    }
    
    describe("validateActiveToken") {
        context("활성화된 토큰을 검증할 때") {
            it("토큰을 반환해야 한다") {
                // given
                val token = "active-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) } returns Unit
                
                // when
                val result = tokenUseCase.validateActiveToken(token)
                
                // then
                result shouldBe waitingToken
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) }
            }
        }
        
        context("비활성화된 토큰을 검증할 때") {
            it("TokenActivationException을 던져야 한다") {
                // given
                val token = "inactive-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING
                every { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.WAITING) } throws TokenActivationException("활성화된 토큰이 아닙니다.")
                
                // when & then
                shouldThrow<TokenActivationException> {
                    tokenUseCase.validateActiveToken(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.WAITING) }
            }
        }
        
        context("존재하지 않는 토큰을 검증할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.EXPIRED
                every { tokenDomainService.validateActiveToken(null, TokenStatus.EXPIRED) } throws TokenNotFoundException("유효하지 않은 토큰입니다.")
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    tokenUseCase.validateActiveToken(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(null, TokenStatus.EXPIRED) }
            }
        }
    }
    
    describe("completeReservation") {
        context("예약/결제를 완료할 때") {
            it("토큰을 완료 처리해야 한다") {
                // given
                val token = "complete-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.completeToken(token) } returns Unit
                
                // when
                tokenUseCase.completeReservation(token)
                
                // then
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.completeToken(token) }
            }
        }
        
        context("존재하지 않는 토큰으로 완료 처리할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    tokenUseCase.completeReservation(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
            }
        }
    }
    
    describe("processQueueAutomatically") {
        context("대기열을 자동으로 처리할 때") {
            it("만료된 토큰을 정리하고 대기열을 처리해야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokens() } returns Unit
                every { queueManager.processQueueAutomatically() } returns Unit
                
                // when
                tokenUseCase.processQueueAutomatically()
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokens() }
                verify { queueManager.processQueueAutomatically() }
            }
        }
    }
    
    describe("cleanupExpiredActiveTokens") {
        context("만료된 활성 토큰을 정리할 때") {
            it("만료된 토큰들을 정리해야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokens() } returns Unit
                
                // when
                tokenUseCase.cleanupExpiredActiveTokens()
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokens() }
            }
        }
    }
})
