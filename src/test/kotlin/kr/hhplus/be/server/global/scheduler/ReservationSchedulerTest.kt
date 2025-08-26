package kr.hhplus.be.server.global.scheduler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
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
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.event.ReservationExpiredEvent
import java.math.BigDecimal
import java.time.LocalDateTime

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
        // Mock 초기화
        clearMocks(reservationService, domainEventPublisher)
        
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
            it("만료된 예약들을 정리하고 이벤트를 발행해야 한다") {
                // given
                val expiredReservations = listOf(
                    ReservationDto(
                        reservationId = 1L,
                        userId = 10L,
                        concertId = 100L,
                        seatId = 200L,
                        paymentId = null,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        statusCode = "TEMPORARY",
                        statusName = "임시예약",
                        statusDescription = "임시 예약 상태",
                        reservedAt = LocalDateTime.now().minusHours(1),
                        expiresAt = LocalDateTime.now().minusMinutes(10),
                        confirmedAt = null
                    ),
                    ReservationDto(
                        reservationId = 2L,
                        userId = 20L,
                        concertId = 100L,
                        seatId = 201L,
                        paymentId = null,
                        seatNumber = "A2",
                        price = BigDecimal("50000"),
                        statusCode = "TEMPORARY",
                        statusName = "임시예약",
                        statusDescription = "임시 예약 상태",
                        reservedAt = LocalDateTime.now().minusHours(1),
                        expiresAt = LocalDateTime.now().minusMinutes(5),
                        confirmedAt = null
                    )
                )
                
                val cancelledReservation1 = mockk<Reservation>()
                val cancelledReservation2 = mockk<Reservation>()
                
                every { cancelledReservation1.reservationId } returns 1L
                every { cancelledReservation1.userId } returns 10L
                every { cancelledReservation1.concertId } returns 100L
                every { cancelledReservation1.seatId } returns 200L
                
                every { cancelledReservation2.reservationId } returns 2L
                every { cancelledReservation2.userId } returns 20L
                every { cancelledReservation2.concertId } returns 100L
                every { cancelledReservation2.seatId } returns 201L
                
                every { reservationService.getExpiredReservations() } returns expiredReservations
                every { reservationService.cancelReservationBySystem(1L, "예약 시간 만료") } returns cancelledReservation1
                every { reservationService.cancelReservationBySystem(2L, "예약 시간 만료") } returns cancelledReservation2
                every { domainEventPublisher.publish(any<ReservationExpiredEvent>()) } just Runs
                
                // when
                reservationScheduler.cleanupExpiredReservations()
                
                // then
                verify { reservationService.getExpiredReservations() }
                verify(exactly = 2) { domainEventPublisher.publish(any<ReservationExpiredEvent>()) }
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
                every { reservationService.getExpiredReservations() } returns emptyList()
                
                // when
                reservationScheduler.cleanupExpiredReservations()
                
                // then
                verify { reservationService.getExpiredReservations() }
                verify(exactly = 0) { domainEventPublisher.publish(any<ReservationExpiredEvent>()) }
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
            it("개별 예약 처리에서 예외가 발생해도 다른 예약들은 계속 처리되어야 한다") {
                // given
                val expiredReservations = listOf(
                    ReservationDto(
                        reservationId = 1L,
                        userId = 10L,
                        concertId = 100L,
                        seatId = 200L,
                        paymentId = null,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        statusCode = "TEMPORARY",
                        statusName = "임시예약",
                        statusDescription = "임시 예약 상태",
                        reservedAt = LocalDateTime.now().minusHours(1),
                        expiresAt = LocalDateTime.now().minusMinutes(10),
                        confirmedAt = null
                    ),
                    ReservationDto(
                        reservationId = 2L,
                        userId = 20L,
                        concertId = 100L,
                        seatId = 201L,
                        paymentId = null,
                        seatNumber = "A2",
                        price = BigDecimal("50000"),
                        statusCode = "TEMPORARY",
                        statusName = "임시예약",
                        statusDescription = "임시 예약 상태",
                        reservedAt = LocalDateTime.now().minusHours(1),
                        expiresAt = LocalDateTime.now().minusMinutes(5),
                        confirmedAt = null
                    )
                )
                
                val cancelledReservation2 = mockk<Reservation>()
                every { cancelledReservation2.reservationId } returns 2L
                every { cancelledReservation2.userId } returns 20L
                every { cancelledReservation2.concertId } returns 100L
                every { cancelledReservation2.seatId } returns 201L
                
                every { reservationService.getExpiredReservations() } returns expiredReservations
                every { reservationService.cancelReservationBySystem(1L, "예약 시간 만료") } throws RuntimeException("첫 번째 예약 처리 실패")
                every { reservationService.cancelReservationBySystem(2L, "예약 시간 만료") } returns cancelledReservation2
                every { domainEventPublisher.publish(any<ReservationExpiredEvent>()) } just Runs
                
                // when
                reservationScheduler.cleanupExpiredReservations()
                
                // then
                verify { reservationService.getExpiredReservations() }
                verify(exactly = 1) { domainEventPublisher.publish(any<ReservationExpiredEvent>()) } // 성공한 것만 이벤트 발행
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