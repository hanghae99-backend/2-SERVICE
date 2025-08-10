package kr.hhplus.be.server.api.payment.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import java.math.BigDecimal
import java.time.LocalDateTime

class ProcessPaymentUseCaseTest : DescribeSpec({

    describe("execute") {
        context("정상적인 결제 요청일 때") {
            it("결제를 처리하고 예약을 확정해야 한다") {
                // given
                val paymentService = mockk<PaymentService>(relaxed = true)
                val reservationService = mockk<ReservationService>(relaxed = true)
                val seatService = mockk<SeatService>(relaxed = true)
                val balanceService = mockk<BalanceService>(relaxed = true)
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>(relaxed = true)
                val tokenDomainService = mockk<TokenDomainService>(relaxed = true)
                val tokenLifecycleManager = mockk<TokenLifecycleManager>(relaxed = true)
                val processPaymentUseCase = ProcessPaymentUserCase(
                    paymentService,
                    reservationService,
                    seatService,
                    balanceService,
                    deductBalanceUseCase,
                    tokenDomainService,
                    tokenLifecycleManager
                )

                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val paymentAmount = BigDecimal("50000")
                
                val mockToken = mockk<WaitingToken>()
                val mockStatus = TokenStatus.ACTIVE
                val mockReservation = mockk<Reservation>(relaxed = true)
                val mockSeat = mockk<SeatDto> {
                    every { price } returns paymentAmount
                }
                val mockBalance = mockk<Point>(relaxed = true)
                val mockPayment = mockk<PaymentDto>()
                val mockCompletedPayment = mockk<PaymentDto>()
                
                every { mockReservation.userId } returns userId
                every { mockReservation.seatId } returns seatId
                every { mockReservation.isTemporary() } returns true
                every { mockReservation.isExpired() } returns false
                every { mockBalance.amount } returns BigDecimal("100000")
                every { mockPayment.paymentId } returns 1L
                every { mockPayment.amount } returns paymentAmount
                
                // Mock 설정 - relaxed이므로 필요한 것만
                every { tokenLifecycleManager.findToken(any()) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(any()) } returns mockStatus
                every { reservationService.getReservationWithLock(any()) } returns mockReservation
                every { seatService.getSeatById(any()) } returns mockSeat
                every { paymentService.createReservationPayment(any(), any(), any()) } returns mockPayment
                every { balanceService.getBalance(any()) } returns mockBalance
                every { paymentService.completePayment(any(), any(), any(), any()) } returns mockCompletedPayment

                // when
                val result = processPaymentUseCase.execute(userId, reservationId, token)

                // then
                result shouldBe mockCompletedPayment
                verify { paymentService.createReservationPayment(any(), any(), any()) }
                verify { paymentService.completePayment(any(), any(), any(), any()) }
            }
        }

        context("다른 사용자의 예약으로 결제할 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val paymentService = mockk<PaymentService>(relaxed = true)
                val reservationService = mockk<ReservationService>(relaxed = true)
                val seatService = mockk<SeatService>(relaxed = true)
                val balanceService = mockk<BalanceService>(relaxed = true)
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>(relaxed = true)
                val tokenDomainService = mockk<TokenDomainService>(relaxed = true)
                val tokenLifecycleManager = mockk<TokenLifecycleManager>(relaxed = true)
                val processPaymentUseCase = ProcessPaymentUserCase(
                    paymentService,
                    reservationService,
                    seatService,
                    balanceService,
                    deductBalanceUseCase,
                    tokenDomainService,
                    tokenLifecycleManager
                )

                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                
                val mockToken = mockk<WaitingToken>()
                val mockStatus = TokenStatus.ACTIVE
                val mockReservation = mockk<Reservation>(relaxed = true)
                
                every { mockReservation.userId } returns 2L // 다른 사용자
                
                every { tokenLifecycleManager.findToken(token) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockStatus
                justRun { tokenDomainService.validateActiveToken(mockToken, mockStatus) }
                every { reservationService.getReservationWithLock(any()) } returns mockReservation

                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, token)
                }
            }
        }

        context("임시 예약 상태가 아닐 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val paymentService = mockk<PaymentService>(relaxed = true)
                val reservationService = mockk<ReservationService>(relaxed = true)
                val seatService = mockk<SeatService>(relaxed = true)
                val balanceService = mockk<BalanceService>(relaxed = true)
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>(relaxed = true)
                val tokenDomainService = mockk<TokenDomainService>(relaxed = true)
                val tokenLifecycleManager = mockk<TokenLifecycleManager>(relaxed = true)
                val processPaymentUseCase = ProcessPaymentUserCase(
                    paymentService,
                    reservationService,
                    seatService,
                    balanceService,
                    deductBalanceUseCase,
                    tokenDomainService,
                    tokenLifecycleManager
                )

                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                
                val mockToken = mockk<WaitingToken>()
                val mockStatus = TokenStatus.ACTIVE
                val mockReservation = mockk<Reservation>(relaxed = true)
                
                every { mockReservation.userId } returns userId
                every { mockReservation.isTemporary() } returns false // 임시 예약 아님
                
                every { tokenLifecycleManager.findToken(token) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockStatus
                justRun { tokenDomainService.validateActiveToken(mockToken, mockStatus) }
                every { reservationService.getReservationWithLock(any()) } returns mockReservation

                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, token)
                }
            }
        }

        context("예약이 만료되었을 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val paymentService = mockk<PaymentService>(relaxed = true)
                val reservationService = mockk<ReservationService>(relaxed = true)
                val seatService = mockk<SeatService>(relaxed = true)
                val balanceService = mockk<BalanceService>(relaxed = true)
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>(relaxed = true)
                val tokenDomainService = mockk<TokenDomainService>(relaxed = true)
                val tokenLifecycleManager = mockk<TokenLifecycleManager>(relaxed = true)
                val processPaymentUseCase = ProcessPaymentUserCase(
                    paymentService,
                    reservationService,
                    seatService,
                    balanceService,
                    deductBalanceUseCase,
                    tokenDomainService,
                    tokenLifecycleManager
                )

                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                
                val mockToken = mockk<WaitingToken>()
                val mockStatus = TokenStatus.ACTIVE
                val mockReservation = mockk<Reservation>(relaxed = true)
                
                every { mockReservation.userId } returns userId
                every { mockReservation.isTemporary() } returns true
                every { mockReservation.isExpired() } returns true // 만료됨
                
                every { tokenLifecycleManager.findToken(token) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockStatus
                justRun { tokenDomainService.validateActiveToken(mockToken, mockStatus) }
                every { reservationService.getReservationWithLock(any()) } returns mockReservation

                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, token)
                }
            }
        }

        context("결제 처리 중 오류가 발생할 때") {
            it("결제를 실패 처리하고 PaymentProcessException을 던져야 한다") {
                // given
                val paymentService = mockk<PaymentService>(relaxed = true)
                val reservationService = mockk<ReservationService>(relaxed = true)
                val seatService = mockk<SeatService>(relaxed = true)
                val balanceService = mockk<BalanceService>(relaxed = true)
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>(relaxed = true)
                val tokenDomainService = mockk<TokenDomainService>(relaxed = true)
                val tokenLifecycleManager = mockk<TokenLifecycleManager>(relaxed = true)
                val processPaymentUseCase = ProcessPaymentUserCase(
                    paymentService,
                    reservationService,
                    seatService,
                    balanceService,
                    deductBalanceUseCase,
                    tokenDomainService,
                    tokenLifecycleManager
                )

                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val paymentAmount = BigDecimal("50000")
                
                val mockToken = mockk<WaitingToken>()
                val mockStatus = TokenStatus.ACTIVE
                val mockReservation = mockk<Reservation>(relaxed = true)
                val mockSeat = mockk<SeatDto> {
                    every { price } returns paymentAmount
                }
                val mockBalance = mockk<Point>(relaxed = true)
                val mockPayment = mockk<PaymentDto>()
                
                every { mockReservation.userId } returns userId
                every { mockReservation.seatId } returns seatId
                every { mockReservation.isTemporary() } returns true
                every { mockReservation.isExpired() } returns false
                every { mockBalance.amount } returns BigDecimal("100000")
                every { mockPayment.paymentId } returns 1L
                every { mockPayment.amount } returns paymentAmount
                
                // Mock 설정
                every { tokenLifecycleManager.findToken(any()) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(any()) } returns mockStatus
                every { reservationService.getReservationWithLock(any()) } returns mockReservation
                every { seatService.getSeatById(any()) } returns mockSeat
                every { paymentService.createReservationPayment(any(), any(), any()) } returns mockPayment
                every { balanceService.getBalance(any()) } returns mockBalance
                every { deductBalanceUseCase.execute(any(), any()) } throws RuntimeException("잔액 부족")

                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, token)
                }
            }
        }
    }
})
