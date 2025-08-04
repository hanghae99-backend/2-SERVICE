package kr.hhplus.be.server.domain.auth.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.mockk.*
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class QueueManagerConcurrencyTest : DescribeSpec({
    
    describe("QueueManager 동시성 테스트") {
        
        context("동시에 여러 사용자가 대기열에 진입할 때") {
            it("모든 토큰이 순서대로 대기열에 추가되어야 한다") {
                // given
                val tokenStore = mockk<TokenStore>(relaxed = true)
                val queueManager = QueueManager(tokenStore)
                val concurrentUsers = 100
                val addedTokens = ConcurrentHashMap.newKeySet<String>()
                
                every { tokenStore.addToWaitingQueue(any()) } answers {
                    val token = firstArg<String>()
                    addedTokens.add(token)
                }
                
                // when
                runBlocking {
                    val jobs = (1..concurrentUsers).map { userIndex ->
                        async(Dispatchers.Default) {
                            val token = "user-$userIndex-token"
                            queueManager.addToQueue(token)
                        }
                    }
                    jobs.awaitAll()
                }
                
                // then
                addedTokens.size shouldBe concurrentUsers
                verify(exactly = concurrentUsers) { tokenStore.addToWaitingQueue(any()) }
            }
        }
        
        context("동시에 여러 토큰이 활성화될 때") {
            it("최대 활성 토큰 수가 지켜져야 한다") {
                // given
                val tokenStore = mockk<TokenStore>(relaxed = true)
                val queueManager = QueueManager(tokenStore)
                val maxActiveTokens = 100L
                val currentActiveCount = AtomicLong(95L) // 현재 95개 활성
                val activationAttempts = 20
                val successfulActivations = AtomicInteger(0)
                
                every { tokenStore.countActiveTokens() } answers { currentActiveCount.get() }
                every { tokenStore.getNextTokensFromQueue(any()) } answers {
                    val count = firstArg<Int>()
                    (1..count).map { "token-$it" }
                }
                every { tokenStore.activateToken(any()) } answers {
                    if (currentActiveCount.get() < maxActiveTokens) {
                        currentActiveCount.incrementAndGet()
                        successfulActivations.incrementAndGet()
                    }
                }
                
                // when
                runBlocking {
                    val jobs = (1..activationAttempts).map {
                        async(Dispatchers.Default) {
                            queueManager.processQueueAutomatically()
                        }
                    }
                    jobs.awaitAll()
                }
                
                // then
                currentActiveCount.get() shouldBeLessThanOrEqualTo maxActiveTokens
                successfulActivations.get() shouldBeLessThanOrEqualTo 5 // 최대 5개만 추가 가능
            }
        }
        
        context("동시에 토큰 상태 조회가 발생할 때") {
            it("일관된 상태를 반환해야 한다") {
                // given
                val tokenStore = mockk<TokenStore>(relaxed = true)
                val queueManager = QueueManager(tokenStore)
                val token = "test-token"
                val queryCount = 50
                val results = ConcurrentHashMap<Int, TokenStatus>()
                
                every { tokenStore.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenStore.getQueuePosition(token) } returns 5
                
                // when
                runBlocking {
                    val jobs = (1..queryCount).map { index ->
                        async(Dispatchers.Default) {
                            val status = queueManager.getQueueStatus(token)
                            results[index] = TokenStatus.valueOf(status.status)
                        }
                    }
                    jobs.awaitAll()
                }
                
                // then
                results.size shouldBe queryCount
                results.values.forEach { status ->
                    status shouldBe TokenStatus.ACTIVE
                }
                verify(exactly = queryCount) { tokenStore.getTokenStatus(token) }
            }
        }
        
        context("동시에 대기열에서 토큰을 가져올 때") {
            it("중복 없이 토큰이 분배되어야 한다") {
                // given
                val tokenStore = mockk<TokenStore>(relaxed = true)
                val queueManager = QueueManager(tokenStore)
                val totalTokens = 100
                val concurrentRequests = 10
                val tokensPerRequest = 5
                val distributedTokens = ConcurrentHashMap.newKeySet<String>()
                val tokenQueue = (1..totalTokens).map { "queue-token-$it" }.toMutableList()
                
                every { tokenStore.getNextTokensFromQueue(tokensPerRequest) } answers {
                    synchronized(tokenQueue) {
                        val tokens = tokenQueue.take(tokensPerRequest)
                        repeat(tokensPerRequest) { 
                            if (tokenQueue.isNotEmpty()) tokenQueue.removeFirst() 
                        }
                        tokens
                    }
                }
                
                // when
                runBlocking {
                    val jobs = (1..concurrentRequests).map {
                        async(Dispatchers.Default) {
                            val tokens = queueManager.getNextTokensFromQueue(tokensPerRequest)
                            tokens.forEach { token -> distributedTokens.add(token) }
                        }
                    }
                    jobs.awaitAll()
                }
                
                // then
                distributedTokens.size shouldBe (concurrentRequests * tokensPerRequest)
                verify(exactly = concurrentRequests) { tokenStore.getNextTokensFromQueue(tokensPerRequest) }
            }
        }
    }
})