package kr.hhplus.be.server.global.scheduler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.auth.service.TokenService
import kr.hhplus.be.server.domain.reservation.service.ReservationService

class ReservationSchedulerTest : DescribeSpec({
    
    val reservationService = mockk<ReservationService>()
    val tokenService = mockk<TokenService>()
    
    val reservationScheduler = ReservationScheduler(
        reservationService,
        tokenService
    )
    
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
            }
        }
        
        context("예약 정리 중 예외가 발생할 때") {
            it("예외를 처리하고 계속 실행되어야 한다") {
                // given
                every { reservationService.cleanupExpiredReservations() } throws RuntimeException("정리 실패")
                
                // when
                reservationScheduler.cleanupExpiredReservations()
                
                // then
                verify { reservationService.cleanupExpiredReservations() }
            }
        }
    }
    
    describe("processQueue") {
        context("대기열 자동 처리를 실행할 때") {
            it("토큰 서비스의 자동 처리를 호출해야 한다") {
                // given
                every { tokenService.processQueueAutomatically() } returns Unit
                
                // when
                reservationScheduler.processQueue()
                
                // then
                verify { tokenService.processQueueAutomatically() }
            }
        }
        
        context("대기열 처리 중 예외가 발생할 때") {
            it("예외를 처리하고 계속 실행되어야 한다") {
                // given
                every { tokenService.processQueueAutomatically() } throws RuntimeException("처리 실패")
                
                // when
                reservationScheduler.processQueue()
                
                // then
                verify { tokenService.processQueueAutomatically() }
            }
        }
    }
    
    describe("cleanupExpiredTokens") {
        context("만료된 토큰 정리를 실행할 때") {
            it("토큰 서비스의 정리 메서드를 호출해야 한다") {
                // given
                every { tokenService.cleanupExpiredActiveTokens() } returns Unit
                
                // when
                reservationScheduler.cleanupExpiredTokens()
                
                // then
                verify { tokenService.cleanupExpiredActiveTokens() }
            }
        }
        
        context("토큰 정리 중 예외가 발생할 때") {
            it("예외를 처리하고 계속 실행되어야 한다") {
                // given
                every { tokenService.cleanupExpiredActiveTokens() } throws RuntimeException("정리 실패")
                
                // when
                reservationScheduler.cleanupExpiredTokens()
                
                // then
                verify { tokenService.cleanupExpiredActiveTokens() }
            }
        }
    }
})