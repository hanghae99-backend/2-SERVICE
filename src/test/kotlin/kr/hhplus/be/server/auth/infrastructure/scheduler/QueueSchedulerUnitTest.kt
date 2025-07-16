package kr.hhplus.be.server.auth.infrastructure.scheduler

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.auth.infrastructure.QueueScheduler
import kr.hhplus.be.server.auth.service.QueueStatusResponse
import kr.hhplus.be.server.auth.service.TokenService

class QueueSchedulerUnitTest : BehaviorSpec({
    lateinit var tokenService: TokenService
    lateinit var queueScheduler: QueueScheduler

    beforeTest {
        tokenService = mockk()
        queueScheduler = QueueScheduler(tokenService)
        clearMocks(tokenService, answers = false, recordedCalls = true)
    }

    given("QueueScheduler - 콘서트 예약 서비스") {
        `when`("대기열 처리 스케줄러가 실행될 때") {
            then("토큰 서비스의 자동 큐 처리가 호출된다") {
                // given
                every { tokenService.processQueueAutomatically() } just Runs

                // when
                queueScheduler.processWaitingQueue()

                // then
                verify(exactly = 1) { tokenService.processQueueAutomatically() }
            }
        }
        `when`("대기열 처리 중 예외가 발생할 때") {
            then("예외를 잡아서 처리하고 계속 진행한다") {
                // given
                every { tokenService.processQueueAutomatically() } throws RuntimeException("Test exception")

                // when & then (예외가 전파되지 않아야 함)
                queueScheduler.processWaitingQueue()

                verify(exactly = 1) { tokenService.processQueueAutomatically() }
            }
        }
        `when`("만료된 토큰 정리 스케줄러가 실행될 때") {
            then("토큰 서비스의 만료 토큰 정리가 호출된다") {
                // given
                every { tokenService.cleanupExpiredActiveTokens() } just Runs

                // when
                queueScheduler.cleanupExpiredTokens()

                // then
                verify(exactly = 1) { tokenService.cleanupExpiredActiveTokens() }
            }
        }
        `when`("만료된 토큰 정리 중 예외가 발생할 때") {
            then("예외를 잡아서 처리하고 계속 진행한다") {
                // given
                every { tokenService.cleanupExpiredActiveTokens() } throws RuntimeException("Cleanup exception")

                // when & then
                queueScheduler.cleanupExpiredTokens()

                verify(exactly = 1) { tokenService.cleanupExpiredActiveTokens() }
            }
        }
        `when`("큐 상태 로깅 스케줄러가 실행될 때") {
            then("토큰 서비스의 큐 상태 조회가 호출된다") {
                // given
                val queueStatus = QueueStatusResponse(50L, 80L, 100L, 20L)
                every { tokenService.getQueueStatus() } returns queueStatus

                // when
                queueScheduler.logQueueStatus()

                // then
                verify(exactly = 1) { tokenService.getQueueStatus() }
            }
        }
        `when`("큐 상태 로깅 중 예외가 발생할 때") {
            then("예외를 잡아서 처리하고 계속 진행한다") {
                // given
                every { tokenService.getQueueStatus() } throws RuntimeException("Log exception")

                // when & then
                queueScheduler.logQueueStatus()

                verify(exactly = 1) { tokenService.getQueueStatus() }
            }
        }
    }
})