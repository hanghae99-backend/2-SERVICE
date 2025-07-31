package kr.hhplus.be.server.domain.auth.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.auth.models.TokenStatus

class QueueManagerTest : DescribeSpec({
    
    val tokenStore = mockk<TokenStore>()
    
    val queueManager = QueueManager(tokenStore)
    
    describe("addToQueue") {
        context("토큰을 대기열에 추가할 때") {
            it("토큰 저장소의 대기열에 추가해야 한다") {
                // given
                val token = "test-token"
                
                every { tokenStore.addToWaitingQueue(token) } returns Unit
                
                // when
                queueManager.addToQueue(token)
                
                // then
                verify { tokenStore.addToWaitingQueue(token) }
            }
        }
    }
    
    describe("calculateAvailableSlots") {
        context("현재 활성 토큰 수가 최대 한도보다 적을 때") {
            it("가용 슬롯 수를 정확히 계산해야 한다") {
                // given
                val currentActiveCount = 70L
                val expectedAvailableSlots = 30 // 100 - 70
                
                every { tokenStore.countActiveTokens() } returns currentActiveCount
                
                // when
                val result = queueManager.calculateAvailableSlots()
                
                // then
                result shouldBe expectedAvailableSlots
                verify { tokenStore.countActiveTokens() }
            }
        }
        
        context("현재 활성 토큰 수가 최대 한도에 도달했을 때") {
            it("0을 반환해야 한다") {
                // given
                val currentActiveCount = 100L
                val expectedAvailableSlots = 0
                
                every { tokenStore.countActiveTokens() } returns currentActiveCount
                
                // when
                val result = queueManager.calculateAvailableSlots()
                
                // then
                result shouldBe expectedAvailableSlots
                verify { tokenStore.countActiveTokens() }
            }
        }
        
        context("현재 활성 토큰 수가 최대 한도를 초과했을 때") {
            it("음수를 반환해야 한다") {
                // given
                val currentActiveCount = 110L
                val expectedAvailableSlots = -10 // 100 - 110
                
                every { tokenStore.countActiveTokens() } returns currentActiveCount
                
                // when
                val result = queueManager.calculateAvailableSlots()
                
                // then
                result shouldBe expectedAvailableSlots
                verify { tokenStore.countActiveTokens() }
            }
        }
    }
    
    describe("getNextTokensFromQueue") {
        context("대기열에서 다음 토큰들을 가져올 때") {
            it("요청한 개수만큼 토큰을 반환해야 한다") {
                // given
                val count = 3
                val expectedTokens = listOf("token1", "token2", "token3")
                
                every { tokenStore.getNextTokensFromQueue(count) } returns expectedTokens
                
                // when
                val result = queueManager.getNextTokensFromQueue(count)
                
                // then
                result shouldBe expectedTokens
                result.size shouldBe count
                verify { tokenStore.getNextTokensFromQueue(count) }
            }
        }
        
        context("요청 개수가 0 이하일 때") {
            it("빈 리스트를 반환해야 한다") {
                // given
                val count = 0
                
                // when
                val result = queueManager.getNextTokensFromQueue(count)
                
                // then
                result shouldBe emptyList()
            }
        }
        
        context("요청 개수가 음수일 때") {
            it("빈 리스트를 반환해야 한다") {
                // given
                val count = -5
                
                // when
                val result = queueManager.getNextTokensFromQueue(count)
                
                // then
                result shouldBe emptyList()
            }
        }
    }
    
    describe("activateToken") {
        context("토큰을 활성화할 때") {
            it("토큰 저장소에서 활성화해야 한다") {
                // given
                val token = "test-token"
                
                every { tokenStore.activateToken(token) } returns Unit
                
                // when
                queueManager.activateToken(token)
                
                // then
                verify { tokenStore.activateToken(token) }
            }
        }
    }
    
    describe("processQueueAutomatically") {
        context("가용 슬롯이 있고 대기자가 있을 때") {
            it("대기자를 가용 슬롯만큼 활성화해야 한다") {
                // given
                val availableSlots = 3
                val tokensToActivate = listOf("token1", "token2", "token3")
                
                every { tokenStore.countActiveTokens() } returns 97L // 100 - 97 = 3 slots
                every { tokenStore.getNextTokensFromQueue(availableSlots) } returns tokensToActivate
                every { tokenStore.activateToken("token1") } returns Unit
                every { tokenStore.activateToken("token2") } returns Unit
                every { tokenStore.activateToken("token3") } returns Unit
                
                // when
                queueManager.processQueueAutomatically()
                
                // then
                verify { tokenStore.countActiveTokens() }
                verify { tokenStore.getNextTokensFromQueue(availableSlots) }
                verify { tokenStore.activateToken("token1") }
                verify { tokenStore.activateToken("token2") }
                verify { tokenStore.activateToken("token3") }
            }
        }
        
        context("가용 슬롯이 없을 때") {
            it("대기자를 활성화하지 않아야 한다") {
                // given
                every { tokenStore.countActiveTokens() } returns 100L // 가용 슬롯 0
                
                // when
                queueManager.processQueueAutomatically()
                
                // then
                verify { tokenStore.countActiveTokens() }
                verify(exactly = 0) { tokenStore.getNextTokensFromQueue(any()) }
                verify(exactly = 0) { tokenStore.activateToken(any()) }
            }
        }
        
        context("토큰 활성화 중 예외가 발생할 때") {
            it("다른 토큰 활성화를 계속해야 한다") {
                // given
                val availableSlots = 3
                val tokensToActivate = listOf("token1", "token2", "token3")
                
                every { tokenStore.countActiveTokens() } returns 97L // 3 slots available
                every { tokenStore.getNextTokensFromQueue(availableSlots) } returns tokensToActivate
                every { tokenStore.activateToken("token1") } returns Unit
                every { tokenStore.activateToken("token2") } throws RuntimeException("활성화 실패")
                every { tokenStore.activateToken("token3") } returns Unit
                
                // when
                queueManager.processQueueAutomatically()
                
                // then
                verify { tokenStore.activateToken("token1") }
                verify { tokenStore.activateToken("token2") }
                verify { tokenStore.activateToken("token3") }
            }
        }
    }
    
    describe("getQueueStatus") {
        context("대기 중인 토큰의 상태를 조회할 때") {
            it("대기 정보가 포함된 상태를 반환해야 한다") {
                // given
                val token = "waiting-token"
                val queuePosition = 5
                
                every { tokenStore.getTokenStatus(token) } returns TokenStatus.WAITING
                every { tokenStore.getQueuePosition(token) } returns queuePosition
                
                // when
                val result = queueManager.getQueueStatus(token)
                
                // then
                result shouldNotBe null
                result.token shouldBe token
                result.status shouldBe "WAITING"
                result.message shouldBe "대기 중입니다"
                result.queuePosition shouldBe queuePosition + 1 // 1부터 시작
                result.estimatedWaitingTime shouldNotBe null
                
                verify { tokenStore.getTokenStatus(token) }
                verify { tokenStore.getQueuePosition(token) }
            }
        }
        
        context("활성화된 토큰의 상태를 조회할 때") {
            it("활성 상태 정보를 반환해야 한다") {
                // given
                val token = "active-token"
                
                every { tokenStore.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenStore.getQueuePosition(token) } returns -1
                
                // when
                val result = queueManager.getQueueStatus(token)
                
                // then
                result shouldNotBe null
                result.token shouldBe token
                result.status shouldBe "ACTIVE"
                result.message shouldBe "토큰이 활성화되었습니다"
                result.queuePosition shouldBe null
                result.estimatedWaitingTime shouldBe null
                
                verify { tokenStore.getTokenStatus(token) }
                verify { tokenStore.getQueuePosition(token) }
            }
        }
    }
    
    describe("getQueuePosition") {
        context("토큰의 대기 순서를 조회할 때") {
            it("해당 토큰의 대기 순서를 반환해야 한다") {
                // given
                val token = "test-token"
                val expectedPosition = 3
                
                every { tokenStore.getQueuePosition(token) } returns expectedPosition
                
                // when
                val result = queueManager.getQueuePosition(token)
                
                // then
                result shouldBe expectedPosition
                verify { tokenStore.getQueuePosition(token) }
            }
        }
    }
})