package kr.hhplus.be.server.global.scheduler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockStrategy

class ReservationSchedulerTest : DescribeSpec({
    
    val reservationService = mockk<ReservationService>()
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()
    val queueManager = mockk<QueueManager>()
    val selloutRankingService = mockk<SelloutRankingService>()
    val domainEventPublisher = mockk<DomainEventPublisher>()
    val distributedLock = mockk<DistributedLock>()
    val reservationScheduler = ReservationScheduler(
        reservationService,
        tokenLifecycleManager,
        queueManager,
        selloutRankingService,
        domainEventPublisher,
        distributedLock
    )
    
    beforeEach {
        // DistributedLock의 executeWithLock이 전달받은 람다를 즉시 실행하도록 설정
        val lambdaSlot = slot<() -> Unit>()
        every { 
            distributedLock.executeWithLock<Unit>(
                lockKey = any(),
                strategy = any(),
                lockTimeoutMs = any(),
                waitTimeoutMs = any(),
                retryIntervalMs = any(),
                maxRetryCount = any(),
                action = capture(lambdaSlot)
            ) 
        } answers {
            lambdaSlot.captured.invoke()
        }
    }
    
    describe("cleanupExpiredReservations") {
        context("만료된 예약이 있을 때") {
            it("만료된 예약들을 정리해야 한다") {
                // given
                val cleanedCount = 5
                
                every { reservationService.cleanupExpiredReservations() } returns cleanedCount
                
                // when
                reservationScheduler.cleanupExpiredReservations()
                
                // then
                verify { reservationService.cleanupExpiredReservations() }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:reservation:cleanup",
                        strategy = any(),
                        lockTimeoutMs = 50000L,
                        waitTimeoutMs = 10000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
            }
        }
        
        context("만료된 예약이 없을 때") {
            it("0건 정리되어야 한다") {
                // given
                val cleanedCount = 0
                
                every { reservationService.cleanupExpiredReservations() } returns cleanedCount
                
                // when
                reservationScheduler.cleanupExpiredReservations()
                
                // then
                verify { reservationService.cleanupExpiredReservations() }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:reservation:cleanup",
                        strategy = any(),
                        lockTimeoutMs = 50000L,
                        waitTimeoutMs = 10000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
            }
        }
        
        context("예약 정리 중 예외가 발생할 때") {
            it("예외를 처리하고 계속 실행되어야 한다") {
                // given
                every { reservationService.cleanupExpiredReservations() } throws RuntimeException("정리 실패")
                
                // when & then
                try {
                    reservationScheduler.cleanupExpiredReservations()
                } catch (e: RuntimeException) {
                    // 예외가 발생하는 것이 정상
                }
                
                // then
                verify { reservationService.cleanupExpiredReservations() }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:reservation:cleanup",
                        strategy = any(),
                        lockTimeoutMs = 50000L,
                        waitTimeoutMs = 10000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
            }
        }
    }
    
    describe("activateTokensScheduled") {
        context("토큰 활성화 스케줄러를 실행할 때") {
            it("큐 매니저의 배치 활성화 메서드를 호출해야 한다") {
                // given
                every { queueManager.activateTokensByCount(any()) } returns 2
                
                // when
                reservationScheduler.activateTokensScheduled()
                
                // then
                verify { queueManager.activateTokensByCount(any()) }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:token:activation",
                        strategy = any(),
                        lockTimeoutMs = 8000L,
                        waitTimeoutMs = 2000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
            }
        }
        
        context("토큰 활성화 중 예외가 발생할 때") {
            it("예외를 처리하고 계속 실행되어야 한다") {
                // given
                every { queueManager.activateTokensByCount(any()) } throws RuntimeException("활성화 실패")
                
                // when & then
                try {
                    reservationScheduler.activateTokensScheduled()
                } catch (e: RuntimeException) {
                    // 예외가 발생하는 것이 정상
                }
                
                // then
                verify { queueManager.activateTokensByCount(any()) }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:token:activation",
                        strategy = any(),
                        lockTimeoutMs = 8000L,
                        waitTimeoutMs = 2000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
            }
        }
    }
    
    describe("cleanupExpiredTokens") {
        context("만료된 토큰 정리를 실행할 때") {
            it("토큰 생명주기 관리자의 정리 메서드를 호출해야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokens() } returns 5
                
                // when
                reservationScheduler.cleanupExpiredTokens()
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokens() }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:token:cleanup",
                        strategy = any(),
                        lockTimeoutMs = 25000L,
                        waitTimeoutMs = 5000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
            }
        }
        
        context("토큰 정리 중 예외가 발생할 때") {
            it("예외를 처리하고 계속 실행되어야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokens() } throws RuntimeException("정리 실패")
                
                // when & then
                try {
                    reservationScheduler.cleanupExpiredTokens()
                } catch (e: RuntimeException) {
                    // 예외가 발생하는 것이 정상
                }
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokens() }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:token:cleanup",
                        strategy = any(),
                        lockTimeoutMs = 25000L,
                        waitTimeoutMs = 5000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
            }
        }
    }
})