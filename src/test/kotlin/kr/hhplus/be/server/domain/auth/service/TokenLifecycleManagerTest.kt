package kr.hhplus.be.server.domain.auth.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore

class TokenLifecycleManagerTest : DescribeSpec({
    
    val tokenStore = mockk<TokenStore>(relaxed = true)
    val queueManager = mockk<QueueManager>(relaxed = true)
    
    val tokenLifecycleManager = TokenLifecycleManager(
        tokenStore,
        queueManager
    )
    
    beforeEach {
        clearAllMocks()
    }
    
    describe("saveToken") {
        context("새로운 토큰을 저장할 때") {
            it("토큰 저장소에 저장해야 한다") {
                // given
                val waitingToken = mockk<WaitingToken>()
                
                every { tokenStore.save(waitingToken) } just Runs
                
                // when
                tokenLifecycleManager.saveToken(waitingToken)
                
                // then
                verify(exactly = 1) { tokenStore.save(waitingToken) }
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
                verify(exactly = 1) { tokenStore.getTokenStatus(token) }
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
                verify(exactly = 1) { tokenStore.findByToken(token) }
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
                verify(exactly = 1) { tokenStore.findByToken(token) }
            }
        }
    }
    
    describe("expireToken") {
        context("토큰을 만료시킬 때") {
            it("토큰 저장소에서 만료 처리해야 한다") {
                // given
                val token = "test-token"
                
                every { tokenStore.expireToken(token) } just Runs
                
                // when
                tokenLifecycleManager.expireToken(token)
                
                // then
                verify(exactly = 1) { tokenStore.expireToken(token) }
            }
        }
    }
    
    describe("cleanupExpiredTokens") {
        context("만료된 토큰들이 있을 때") {
            it("만료된 토큰들을 정리해야 한다") {
                // given
                val expiredTokens = listOf("token1", "token2", "token3")
                
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                every { tokenStore.expireToken(any()) } just Runs
                
                // when
                tokenLifecycleManager.cleanupExpiredTokens()
                
                // then
                verify(exactly = 1) { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 1) { tokenStore.expireToken("token1") }
                verify(exactly = 1) { tokenStore.expireToken("token2") }
                verify(exactly = 1) { tokenStore.expireToken("token3") }
            }
        }
        
        context("만료된 토큰이 없을 때") {
            it("토큰 조회만 수행하고 만료 처리는 하지 않아야 한다") {
                // given
                every { tokenStore.findExpiredActiveTokens() } returns emptyList()
                
                // when
                tokenLifecycleManager.cleanupExpiredTokens()
                
                // then
                verify(exactly = 1) { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 0) { tokenStore.expireToken(any()) }
            }
        }
        
        context("만료 처리 중 예외가 발생할 때") {
            it("다른 토큰 처리를 계속해야 한다") {
                // given
                val expiredTokens = listOf("token1", "token2", "token3")
                
                every { tokenStore.findExpiredActiveTokens() } returns expiredTokens
                every { tokenStore.expireToken("token1") } just Runs
                every { tokenStore.expireToken("token2") } throws RuntimeException("처리 실패")
                every { tokenStore.expireToken("token3") } just Runs
                
                // when
                tokenLifecycleManager.cleanupExpiredTokens()
                
                // then
                verify(exactly = 1) { tokenStore.findExpiredActiveTokens() }
                verify(exactly = 1) { tokenStore.expireToken("token1") }
                verify(exactly = 1) { tokenStore.expireToken("token2") }
                verify(exactly = 1) { tokenStore.expireToken("token3") }
            }
        }
    }
    
    describe("completeToken") {
        context("토큰을 완료 처리할 때") {
            it("토큰을 만료시키고 대기열을 자동 처리해야 한다") {
                // given
                val token = "complete-token"
                
                every { tokenStore.expireToken(token) } just Runs
                every { queueManager.processQueueAutomatically() } just Runs
                
                // when
                tokenLifecycleManager.completeToken(token)
                
                // then
                verify(exactly = 1) { tokenStore.expireToken(token) }
                verify(exactly = 1) { queueManager.processQueueAutomatically() }
            }
        }
    }
})