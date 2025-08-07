package kr.hhplus.be.server.api.auth.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.auth.usecase.TokenQueueStatusUseCase
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager

class TokenQueueStatusUseCaseTest : DescribeSpec({
    
    val tokenDomainService = mockk<TokenDomainService>()
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()
    val queueManager = mockk<QueueManager>()
    
    val tokenQueueStatusUseCase = TokenQueueStatusUseCase(
        tokenDomainService,
        tokenLifecycleManager,
        queueManager
    )
    
    describe("execute") {
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
                val result = tokenQueueStatusUseCase.execute(token)
                
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
                val result = tokenQueueStatusUseCase.execute(token)
                
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
                val result = tokenQueueStatusUseCase.execute(token)
                
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
                    tokenQueueStatusUseCase.execute(token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
            }
        }
    }
})
