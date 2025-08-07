package kr.hhplus.be.server.api.auth.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.auth.usecase.TokenIssueUseCase
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager

class TokenIssueUseCaseTest : DescribeSpec({
    
    val tokenDomainService = mockk<TokenDomainService>()
    val tokenFactory = mockk<TokenFactory>()
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()
    val queueManager = mockk<QueueManager>()
    
    val tokenIssueUseCase = TokenIssueUseCase(
        tokenDomainService,
        tokenFactory,
        tokenLifecycleManager,
        queueManager
    )
    
    describe("execute") {
        context("유효한 사용자가 토큰을 발급받을 때") {
            it("대기 토큰을 발급하고 대기열에 추가해야 한다") {
                // given
                val userId = 1L
                val waitingToken = mockk<WaitingToken> {
                    every { token } returns "test-token"
                }
                val queuePosition = 4 // 0-based index
                val estimatedWaitingTime = 10
                
                every { tokenFactory.createWaitingToken(userId) } returns waitingToken
                every { tokenLifecycleManager.saveToken(waitingToken) } returns Unit
                every { queueManager.addToQueue("test-token") } returns Unit
                every { queueManager.getQueuePosition("test-token") } returns queuePosition
                every { tokenDomainService.calculateWaitingTime(queuePosition + 1) } returns estimatedWaitingTime
                
                // when
                val result = tokenIssueUseCase.execute(userId)
                
                // then
                result shouldNotBe null
                result.token shouldBe "test-token"
                result.status shouldBe "WAITING"
                result.queuePosition shouldBe queuePosition + 1
                result.estimatedWaitingTime shouldBe estimatedWaitingTime
                result.message shouldBe "대기열에 등록되었습니다"
                
                verify { tokenFactory.createWaitingToken(userId) }
                verify { tokenLifecycleManager.saveToken(waitingToken) }
                verify { queueManager.addToQueue("test-token") }
                verify { queueManager.getQueuePosition("test-token") }
                verify { tokenDomainService.calculateWaitingTime(queuePosition + 1) }
            }
        }
        
        // @ValidateUserId annotation handles user validation
        // No need to test UserNotFoundException at this level
    }
})
