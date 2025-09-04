package kr.hhplus.be.server.domain.auth.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.kafka.TokenQueueProducer
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.models.TokenStatus

class AuthServiceTest : BehaviorSpec({
    
    val tokenQueueProducer = mockk<TokenQueueProducer>()
    val tokenFactory = mockk<TokenFactory>()
    val authService = AuthService(tokenQueueProducer, tokenFactory)
    
    Given("인증 서비스에서") {
        
        When("토큰을 발급할 때") {
            val userId = 1L
            val waitingToken = WaitingToken.create("test-token-123", userId)
            
            every { tokenFactory.createWaitingToken(userId) } returns waitingToken
            every { tokenQueueProducer.sendTokenToQueue(waitingToken) } returns Unit
            
            val result = authService.issueToken(userId)
            
            Then("대기 토큰이 생성되고 큐에 전송되어야 한다") {
                verify { tokenFactory.createWaitingToken(userId) }
                verify { tokenQueueProducer.sendTokenToQueue(waitingToken) }
                
                result.token shouldBe waitingToken.token
                result.status shouldBe "WAITING"
                result.message shouldBe "대기열에 등록되었습니다"
                result.userId shouldBe userId
                result.issuedAt shouldNotBe null
            }
        }
        
        When("토큰 상태를 조회할 때") {
            val token = "test-token"
            
            val result = authService.getTokenStatusDetail(token)
            
            Then("토큰의 현재 상태를 반환해야 한다") {
                result.token shouldBe token
                result.status shouldBe "WAITING"
                result.message shouldBe "대기 중입니다"
            }
        }
    }
})
