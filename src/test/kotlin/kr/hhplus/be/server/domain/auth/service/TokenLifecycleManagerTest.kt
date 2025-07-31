package kr.hhplus.be.server.domain.auth.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore

class TokenLifecycleManagerTest : DescribeSpec({
    
    val tokenStore = mockk<TokenStore>()
    val queueManager = mockk<QueueManager>()
    
    val tokenLifecycleManager = TokenLifecycleManager(
        tokenStore,
        queueManager
    )
    
    describe("saveToken") {
        context("새로운 토큰을 저장할 때") {
            it("토큰 저장소에 저장해야 한다") {
                // given
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenStore.save(waitingToken) } returns Unit
                
                // when
                tokenLifecycleManager.saveToken(waitingToken)
                
                // then
                verify { tokenStore.save(waitingToken) }
            }
        }
    }
    
    describe("getTokenStatus") {
        context("존재하는 토큰의 상태를 조회할 때") {
            it("해당 토큰의 상태를 반환해야 한다") {
                // given
                val token = "test-token"
                val expectedStatus = TokenStatus.ACTIVE
                
                every { tokenStore.getTokenStatus(token) } returns expectedStatus
                
                // when
                val result = tokenLifecycleManager.getTokenStatus(token)
                
                // then
                result shouldBe expectedStatus
                verify { tokenStore.getTokenStatus(token) }
            }
        }
    }
    
    describe("findToken") {
        context("존재하는 토큰을 조회할 때") {
            it("토큰 객체를 반환해야 한다") {
                // given
                val token = "test-token"
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenStore.findByToken(token) } returns waitingToken
                
                // when
                val result = tokenLifecycleManager.findToken(token)
                
                // then
                result shouldBe waitingToken
                verify { tokenStore.findByToken(token) }
            }
        }
        
        context("존재하지 않는 토큰을 조회할 때") {
            it("null을 반환해야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenStore.findByToken(token) } returns null
                
                // when
                val result = tokenLifecycleManager.findToken(token)
                
                // then
                result shouldBe null
                verify { tokenStore.findByToken(token) }
            }
        }
    }
    
    describe("expireToken") {
        context("토큰을 만료시킬 때") {
            it("토큰 저장소에서 만료 처리해야 한다") {
                // given
                val token = "test-token"
                
                every { tokenStore.expireToken(token) } returns Unit
                
                // when
                tokenLifecycleManager.expireToken(token)
                
                // then
                verify { tokenStore.expireToken(token) }
            }
        }
    }
    
    describe("cleanupExpiredTokens") {
        context("만료된 토큰들이 있을 때") {
            it("만료된 토큰들을 정리해야 한다") {
                // given
                val expiredTokens = listOf("token1", "token2", "token3")
                
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                every { tokenStore.expireToken("token1") } returns Unit
                every { tokenStore.expireToken("token2") } returns Unit
                every { tokenStore.expireToken("token3") } returns Unit
                
                // when
                tokenLifecycleManager.cleanupExpiredTokens()
                
                // then
                verify { tokenStore.findExpiredActiveTokens() }
                verify { tokenStore.expireToken("token1") }
                verify { tokenStore.expireToken("token2") }
                verify { tokenStore.expireToken("token3") }
            }
        }
        
        context("만료된 토큰이 없을 때") {
            it("아무 작업도 하지 않아야 한다") {
                // given
                val expiredTokens = emptyList<String>()
                
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                
                // when
                tokenLifecycleManager.cleanupExpiredTokens()
                
                // then
                verify { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 0) { tokenStore.expireToken(any()) }
            }
        }
        
        context("만료 처리 중 예외가 발생할 때") {
            it("다른 토큰 처리를 계속해야 한다") {
                // given
                val expiredTokens = listOf("token1", "token2", "token3")
                
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                every { tokenStore.expireToken("token1") } returns Unit
                every { tokenStore.expireToken("token2") } throws RuntimeException("처리 실패")
                every { tokenStore.expireToken("token3") } returns Unit
                
                // when
                tokenLifecycleManager.cleanupExpiredTokens()
                
                // then
                verify { tokenStore.findExpiredActiveTokens() }
                verify { tokenStore.expireToken("token1") }
                verify { tokenStore.expireToken("token2") }
                verify { tokenStore.expireToken("token3") }
            }
        }
    }
    
    describe("completeToken") {
        context("토큰을 완료 처리할 때") {
            it("토큰을 만료시키고 대기열을 자동 처리해야 한다") {
                // given
                val token = "complete-token"
                
                every { tokenStore.expireToken(token) } returns Unit
                every { queueManager.processQueueAutomatically() } returns Unit
                
                // when
                tokenLifecycleManager.completeToken(token)
                
                // then
                verify { tokenStore.expireToken(token) }
                verify { queueManager.processQueueAutomatically() }
            }
        }
    }
})