package kr.hhplus.be.server.api.payment.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import java.math.BigDecimal
import java.time.LocalDateTime

class ProcessPaymentUseCaseTest : DescribeSpec({
    
    val paymentService = mockk<PaymentService>()
    val reservationService = mockk<ReservationService>()
    val seatService = mockk<SeatService>()
    val deductBalanceUseCase = mockk<DeductBalanceUseCase>()
    val tokenDomainService = mockk<TokenDomainService>()
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()
    
    val processPaymentUseCase = ProcessPaymentUseCase(
        paymentService,
        reservationService,
        seatService,
        deductBalanceUseCase,
        tokenDomainService,
        tokenLifecycleManager
    )
    
    describe("execute") {
        context("유효한 결제 요청 시") {
            it("결제가 성공적으로 처리되어야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val seatId = 1L
                val token = "valid-token"
                val amount = BigDecimal("50000")
                
                val mockWaitingToken = mockk<WaitingToken>()
                val mockTokenStatus = mockk<TokenStatus>()
                val mockReservation = mockk<Reservation> {
                    every { this@mockk.userId } returns userId
                    every { status.code } returns "TEMPORARY"
                    every { isExpired() } returns false
                }
                val mockSeat = mockk<SeatDto> {
                    every { price } returns amount
                }
                val mockConfirmedSeat = mockk<SeatDto>()
                val mockConfirmedReservation = mockk<Reservation>()
                val mockPoint = mockk<Point>()
                val mockInitialPayment = PaymentDto(
                    paymentId = 1L,
                    userId = userId,
                    reservationId = reservationId,
                    amount = amount,
                    paymentMethod = "POINT",
                    statusCode = "PENDING",
                    paidAt = null
                )
                val mockCompletedPayment = PaymentDto(
                    paymentId = 1L,
                    userId = userId,
                    reservationId = reservationId,
                    amount = amount,
                    paymentMethod = "POINT",
                    statusCode = "COMPLETED",
                    paidAt = LocalDateTime.now()
                )

                every { tokenLifecycleManager.findToken(token) } returns mockWaitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockTokenStatus
                every { tokenDomainService.validateActiveToken(mockWaitingToken, mockTokenStatus) } returns Unit
                every { reservationService.getReservationById(reservationId) } returns mockReservation
                every { seatService.getSeatById(seatId) } returns mockSeat
                every { paymentService.createReservationPayment(userId, reservationId, amount) } returns mockInitialPayment
                every { deductBalanceUseCase.executeInternal(userId, amount) } returns mockPoint
                every { paymentService.failPayment(any(), any(), any(), any()) } returns mockInitialPayment
                every { reservationService.confirmReservation(reservationId, 1L) } returns mockConfirmedReservation
                every { seatService.confirmSeat(seatId) } returns mockConfirmedSeat
                every { paymentService.completePayment(1L, reservationId, seatId, token) } returns mockCompletedPayment
                
                // when
                val result = processPaymentUseCase.execute(userId, reservationId, seatId, token)
                
                // then
                result shouldNotBe null
                result.paymentId shouldBe 1L
                result.amount shouldBe amount
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenDomainService.validateActiveToken(mockWaitingToken, mockTokenStatus) }
                verify { reservationService.getReservationById(reservationId) }
                verify { seatService.getSeatById(seatId) }
                verify { paymentService.createReservationPayment(userId, reservationId, amount) }
                verify { deductBalanceUseCase.executeInternal(userId, amount) }
                verify { reservationService.confirmReservation(reservationId, 1L) }
                verify { seatService.confirmSeat(seatId) }
                verify { paymentService.completePayment(1L, reservationId, seatId, token) }
            }
        }
        
        context("예약이 만료된 경우") {
            it("예외가 발생해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                val mockWaitingToken = mockk<WaitingToken>()
                val mockTokenStatus = mockk<TokenStatus>()
                val mockReservation = mockk<Reservation> {
                    every { this@mockk.userId } returns userId
                    every { status.code } returns "TEMPORARY"
                    every { isExpired() } returns true
                }
                
                every { tokenLifecycleManager.findToken(token) } returns mockWaitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockTokenStatus
                every { tokenDomainService.validateActiveToken(mockWaitingToken, mockTokenStatus) } returns Unit
                every { reservationService.getReservationById(reservationId) } returns mockReservation
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, seatId, token)
                }
            }
        }
        
        context("예약 사용자가 다른 경우") {
            it("예외가 발생해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                val mockWaitingToken = mockk<WaitingToken>()
                val mockTokenStatus = mockk<TokenStatus>()
                val mockReservation = mockk<Reservation> {
                    every { this@mockk.userId } returns 2L // 다른 사용자
                    every { status.code } returns "TEMPORARY"
                    every { isExpired() } returns false
                }
                
                every { tokenLifecycleManager.findToken(token) } returns mockWaitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockTokenStatus
                every { tokenDomainService.validateActiveToken(mockWaitingToken, mockTokenStatus) } returns Unit
                every { reservationService.getReservationById(reservationId) } returns mockReservation
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, seatId, token)
                }
            }
        }
    }
})