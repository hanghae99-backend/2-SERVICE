package kr.hhplus.be.server.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.*
import kr.hhplus.be.server.auth.repository.TokenStore
import kr.hhplus.be.server.auth.service.QueueManager

class QueueManagerUnitTest : BehaviorSpec({
    lateinit var tokenStore: TokenStore
    lateinit var queueManager: QueueManager

    beforeTest {
        tokenStore = mockk()
        queueManager = QueueManager(tokenStore)
        clearMocks(tokenStore, answers = false, recordedCalls = true)
    }

    given("QueueManager는 대기열 관리의 단일 책임을 가진다") {
        `when`("대기열 추가를 요청받으면") {
            then("TokenStore에 위임하여 토큰을 대기열에 추가한다") {
                // given
                val token = "test-token"
                every { tokenStore.addToWaitingQueue(token) } just Runs

                // when
                queueManager.addToQueue(token)

                // then - QueueManager의 책임: 대기열 관리 위임
                verify(exactly = 1) { tokenStore.addToWaitingQueue(token) }
            }
        }

        `when`("가용 슬롯 계산을 요청받으면") {
            then("현재 활성 토큰 수를 기반으로 가용 슬롯을 계산한다") {
                // given
                val currentActiveTokens = 85L
                val expectedAvailableSlots = 15 // MAX_ACTIVE_TOKENS(100) - 85
                every { tokenStore.countActiveTokens() } returns currentActiveTokens

                // when
                val availableSlots = queueManager.calculateAvailableSlots()

                // then - QueueManager의 책임: 가용 슬롯 계산 로직
                availableSlots shouldBe expectedAvailableSlots
                verify(exactly = 1) { tokenStore.countActiveTokens() }
            }
        }

        `when`("대기열에서 토큰 추출을 요청받으면") {
            then("지정된 개수만큼 TokenStore에서 가져온다") {
                // given
                val count = 3
                val expectedTokens = listOf("token1", "token2", "token3")
                every { tokenStore.getNextTokensFromQueue(count) } returns expectedTokens

                // when
                val result = queueManager.getNextTokensFromQueue(count)

                // then - QueueManager의 책임: 대기열 추출 위임
                result shouldContainExactly expectedTokens
                verify(exactly = 1) { tokenStore.getNextTokensFromQueue(count) }
            }
        }

        `when`("개수가 0 이하로 요청되면") {
            then("빈 리스트를 반환한다") {
                // when
                val result = queueManager.getNextTokensFromQueue(0)

                // then - QueueManager의 책임: 경계값 처리
                result.shouldBeEmpty()
                verify(exactly = 0) { tokenStore.getNextTokensFromQueue(any()) }
            }
        }

        `when`("자동 대기열 처리를 요청받으면") {
            then("가용 슬롯을 계산하고 해당 개수만큼 토큰을 활성화한다") {
                // given
                val currentActiveTokens = 97L
                val availableSlots = 3
                val tokensToActivate = listOf("token1", "token2", "token3")
                
                every { tokenStore.countActiveTokens() } returns currentActiveTokens
                every { tokenStore.getNextTokensFromQueue(availableSlots) } returns tokensToActivate
                every { tokenStore.activateToken(any()) } just Runs

                // when
                queueManager.processQueueAutomatically()

                // then - QueueManager의 책임: 자동 처리 로직 조정
                verify(exactly = 1) { tokenStore.countActiveTokens() }
                verify(exactly = 1) { tokenStore.getNextTokensFromQueue(availableSlots) }
                verify(exactly = 3) { tokenStore.activateToken(any()) }
            }
        }

        `when`("가용 슬롯이 없을 때 자동 처리를 요청받으면") {
            then("아무 작업도 하지 않는다") {
                // given
                val currentActiveTokens = 100L // 최대치
                every { tokenStore.countActiveTokens() } returns currentActiveTokens

                // when
                queueManager.processQueueAutomatically()

                // then - QueueManager의 책임: 비효율적 작업 방지
                verify(exactly = 1) { tokenStore.countActiveTokens() }
                verify(exactly = 0) { tokenStore.getNextTokensFromQueue(any()) }
                verify(exactly = 0) { tokenStore.activateToken(any()) }
            }
        }

        `when`("큐 상태 조회를 요청받으면") {
            then("전체 대기열 상태를 수집하여 반환한다") {
                // given
                val queueSize = 150L
                val activeTokens = 85L
                val expectedAvailableSlots = 15L
                
                every { tokenStore.getQueueSize() } returns queueSize
                every { tokenStore.countActiveTokens() } returns activeTokens

                // when
                val status = queueManager.getQueueStatus()

                // then - QueueManager의 책임: 상태 정보 수집 및 결합
                status.queueSize shouldBe queueSize
                status.activeTokens shouldBe activeTokens
                status.maxActiveTokens shouldBe 100L
                status.availableSlots shouldBe expectedAvailableSlots
                
                verify(exactly = 1) { tokenStore.getQueueSize() }
                verify(exactly = 1) { tokenStore.countActiveTokens() }
            }
        }

        `when`("대기 순서 조회를 요청받으면") {
            then("TokenStore에 위임하여 토큰의 대기 순서를 반환한다") {
                // given
                val token = "user-token-123"
                val expectedPosition = 5 // 6번째 대기자
                every { tokenStore.getQueuePosition(token) } returns expectedPosition

                // when
                val position = queueManager.getQueuePosition(token)

                // then - QueueManager의 책임: 대기 순서 조회 위임
                position shouldBe expectedPosition
                verify(exactly = 1) { tokenStore.getQueuePosition(token) }
            }
        }

        `when`("대기열에 없는 토큰의 순서를 조회할 때") {
            then("-1을 반환한다") {
                // given
                val nonExistentToken = "non-existent-token"
                every { tokenStore.getQueuePosition(nonExistentToken) } returns -1

                // when
                val position = queueManager.getQueuePosition(nonExistentToken)

                // then - QueueManager의 책임: 존재하지 않는 토큰 처리
                position shouldBe -1
                verify(exactly = 1) { tokenStore.getQueuePosition(nonExistentToken) }
            }
        }
    }
})
