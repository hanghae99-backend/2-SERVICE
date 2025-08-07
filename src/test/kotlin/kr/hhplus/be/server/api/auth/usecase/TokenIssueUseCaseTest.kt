package kr.hhplus.be.server.api.auth.usecase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.auth.models.WaitingToken
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
    
    describe("토큰 발급 책임") {
        context("토큰 발급 요청이 들어왔을 때") {
            it("각 의존성에게 적절한 책임을 위임하고 결과를 조합해야 한다") {
                val userId = 1L
                val tokenValue = "waiting-token-12345"
                val waitingToken = mockk<WaitingToken> {
                    every { token } returns tokenValue
                }
                val queuePositionFromZero = 4
                val estimatedWaitingTime = 20
                
                every { tokenFactory.createWaitingToken(userId) } returns waitingToken
                every { tokenLifecycleManager.saveToken(waitingToken) } returns Unit
                every { queueManager.addToQueue(tokenValue) } returns Unit
                every { queueManager.getQueuePosition(tokenValue) } returns queuePositionFromZero
                every { tokenDomainService.calculateWaitingTime(queuePositionFromZero + 1) } returns estimatedWaitingTime
                
                val result = tokenIssueUseCase.execute(userId)
                
                verify(exactly = 1) { tokenFactory.createWaitingToken(userId) }
                verify(exactly = 1) { tokenLifecycleManager.saveToken(waitingToken) }
                verify(exactly = 1) { queueManager.addToQueue(tokenValue) }
                verify(exactly = 1) { queueManager.getQueuePosition(tokenValue) }
                verify(exactly = 1) { tokenDomainService.calculateWaitingTime(queuePositionFromZero + 1) }
                
                result.token shouldBe tokenValue
                result.status shouldBe "WAITING"
                result.queuePosition shouldBe queuePositionFromZero + 1
                result.estimatedWaitingTime shouldBe estimatedWaitingTime
                result.message shouldBe "대기열에 등록되었습니다"
                result.userId shouldBe userId
            }
        }
        
        context("대기열 위치 계산 책임") {
            it("0-based 인덱스를 1-based로 변환하여 사용자에게 제공해야 한다") {
                val userId = 1L
                val waitingToken = mockk<WaitingToken> {
                    every { token } returns "test-token"
                }
                val zeroBasedPosition = 0
                
                every { tokenFactory.createWaitingToken(userId) } returns waitingToken
                every { tokenLifecycleManager.saveToken(any()) } returns Unit
                every { queueManager.addToQueue(any()) } returns Unit
                every { queueManager.getQueuePosition(any()) } returns zeroBasedPosition
                every { tokenDomainService.calculateWaitingTime(any()) } returns 5
                
                val result = tokenIssueUseCase.execute(userId)
                
                result.queuePosition shouldBe 1
                verify { tokenDomainService.calculateWaitingTime(1) }
            }
        }
    }
})
