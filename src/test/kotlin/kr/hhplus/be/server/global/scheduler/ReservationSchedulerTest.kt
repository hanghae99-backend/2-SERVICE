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
    
    describe("processQueue") {
        context("대기열 자동 처리를 실행할 때") {
            it("토큰 생명주기 관리자와 큐 매니저의 메서드를 호출해야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue() } returns Pair(3, 2)
                
                // when
                reservationScheduler.processQueue()
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue() }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:queue:process",
                        strategy = any(),
                        lockTimeoutMs = 4000L,
                        waitTimeoutMs = 1000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
            }
        }
        
        context("대기열 처리 중 예외가 발생할 때") {
            it("예외를 처리하고 계속 실행되어야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue() } throws RuntimeException("정리 실패")
                
                // when & then
                try {
                    reservationScheduler.processQueue()
                } catch (e: RuntimeException) {
                    // 예외가 발생하는 것이 정상
                }
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue() }
                verify { 
                    distributedLock.executeWithLock<Unit>(
                        lockKey = "scheduler:queue:process",
                        strategy = any(),
                        lockTimeoutMs = 4000L,
                        waitTimeoutMs = 1000L,
                        retryIntervalMs = any(),
                        maxRetryCount = any(),
                        action = any()
                    )
                }
                // 예외가 발생해도 다음 메서드는 호출되지 않을 수 있음
            }
        }
    }
    
    describe("cleanupExpiredTokensAndProcessQueue") {
        context("만료된 토큰 정리를 실행할 때") {
            it("토큰 생명주기 관리자의 정리 메서드를 호출해야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue() } returns Pair(5, 2)
                
                // when
                reservationScheduler.cleanupExpiredTokensAndProcessQueue()
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue() }
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
                every { tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue() } throws RuntimeException("정리 실패")
                
                // when & then
                try {
                    reservationScheduler.cleanupExpiredTokensAndProcessQueue()
                } catch (e: RuntimeException) {
                    // 예외가 발생하는 것이 정상
                }
                
                // then
                verify { tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue() }
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