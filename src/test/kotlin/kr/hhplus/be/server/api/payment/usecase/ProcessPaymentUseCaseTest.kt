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
import kr.hhplus.be.server.api.payment.dto.PaymentDto
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
                val paymentService = mockk<PaymentService>()
                val reservationService = mockk<ReservationService>()
                val seatService = mockk<SeatService>()
                val balanceService = mockk<BalanceService>()
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>()
                val tokenDomainService = mockk<TokenDomainService>()
                val tokenLifecycleManager = mockk<TokenLifecycleManager>()
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
                val mockStatus = "ACTIVE"
                val mockReservation = mockk<Reservation>(relaxed = true)
                val mockSeat = mockk<Seat>(relaxed = true)
                val mockBalance = mockk<Point>(relaxed = true)
                val mockPayment = mockk<PaymentDto>()
                val mockCompletedPayment = mockk<PaymentDto>()
                
                every { mockReservation.userId } returns userId
                every { mockReservation.seatId } returns seatId
                every { mockReservation.isTemporary() } returns true
                every { mockReservation.isExpired() } returns false
                every { mockSeat.price } returns paymentAmount
                every { mockBalance.amount } returns BigDecimal("100000")
                every { mockPayment.paymentId } returns 1L
                every { mockPayment.amount } returns paymentAmount
                
                every { tokenLifecycleManager.findToken(token) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockStatus
                justRun { tokenDomainService.validateActiveToken(mockToken, mockStatus) }
                
                every { reservationService.getReservationById(reservationId) } returns mockReservation
                every { seatService.getSeatById(seatId) } returns mockSeat
                every { paymentService.createPayment(userId, paymentAmount) } returns mockPayment
                every { balanceService.getBalance(userId) } returns mockBalance
                justRun { paymentService.validatePaymentAmount(mockBalance.amount, mockPayment.amount) }
                every { deductBalanceUseCase.execute(userId, paymentAmount) } returns mockk()
                justRun { reservationService.confirmReservationInternal(reservationId, mockPayment.paymentId) }
                justRun { seatService.confirmSeatInternal(seatId) }
                every { paymentService.completePayment(mockPayment.paymentId, reservationId, seatId, token) } returns mockCompletedPayment

                // when
                val result = processPaymentUseCase.execute(userId, reservationId, token)

                // then
                result shouldBe mockCompletedPayment
                verify { paymentService.createPayment(userId, paymentAmount) }
                verify { deductBalanceUseCase.execute(userId, paymentAmount) }
                verify { reservationService.confirmReservationInternal(reservationId, mockPayment.paymentId) }
                verify { seatService.confirmSeatInternal(seatId) }
                verify { paymentService.completePayment(mockPayment.paymentId, reservationId, seatId, token) }
            }
        }

        context("다른 사용자의 예약으로 결제할 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val paymentService = mockk<PaymentService>()
                val reservationService = mockk<ReservationService>()
                val seatService = mockk<SeatService>()
                val balanceService = mockk<BalanceService>()
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>()
                val tokenDomainService = mockk<TokenDomainService>()
                val tokenLifecycleManager = mockk<TokenLifecycleManager>()
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
                val mockStatus = "ACTIVE"
                val mockReservation = mockk<Reservation>(relaxed = true)
                
                every { mockReservation.userId } returns 2L // 다른 사용자
                
                every { tokenLifecycleManager.findToken(token) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockStatus
                justRun { tokenDomainService.validateActiveToken(mockToken, mockStatus) }
                every { reservationService.getReservationById(reservationId) } returns mockReservation

                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, token)
                }
            }
        }

        context("임시 예약 상태가 아닐 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val paymentService = mockk<PaymentService>()
                val reservationService = mockk<ReservationService>()
                val seatService = mockk<SeatService>()
                val balanceService = mockk<BalanceService>()
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>()
                val tokenDomainService = mockk<TokenDomainService>()
                val tokenLifecycleManager = mockk<TokenLifecycleManager>()
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
                val mockStatus = "ACTIVE"
                val mockReservation = mockk<Reservation>(relaxed = true)
                
                every { mockReservation.userId } returns userId
                every { mockReservation.isTemporary() } returns false // 임시 예약 아님
                
                every { tokenLifecycleManager.findToken(token) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockStatus
                justRun { tokenDomainService.validateActiveToken(mockToken, mockStatus) }
                every { reservationService.getReservationById(reservationId) } returns mockReservation

                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, token)
                }
            }
        }

        context("예약이 만료되었을 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val paymentService = mockk<PaymentService>()
                val reservationService = mockk<ReservationService>()
                val seatService = mockk<SeatService>()
                val balanceService = mockk<BalanceService>()
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>()
                val tokenDomainService = mockk<TokenDomainService>()
                val tokenLifecycleManager = mockk<TokenLifecycleManager>()
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
                val mockStatus = "ACTIVE"
                val mockReservation = mockk<Reservation>(relaxed = true)
                
                every { mockReservation.userId } returns userId
                every { mockReservation.isTemporary() } returns true
                every { mockReservation.isExpired() } returns true // 만료됨
                
                every { tokenLifecycleManager.findToken(token) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockStatus
                justRun { tokenDomainService.validateActiveToken(mockToken, mockStatus) }
                every { reservationService.getReservationById(reservationId) } returns mockReservation

                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, token)
                }
            }
        }

        context("결제 처리 중 오류가 발생할 때") {
            it("결제를 실패 처리하고 PaymentProcessException을 던져야 한다") {
                // given
                val paymentService = mockk<PaymentService>()
                val reservationService = mockk<ReservationService>()
                val seatService = mockk<SeatService>()
                val balanceService = mockk<BalanceService>()
                val deductBalanceUseCase = mockk<DeductBalanceUseCase>()
                val tokenDomainService = mockk<TokenDomainService>()
                val tokenLifecycleManager = mockk<TokenLifecycleManager>()
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
                val mockStatus = "ACTIVE"
                val mockReservation = mockk<Reservation>(relaxed = true)
                val mockSeat = mockk<Seat>(relaxed = true)
                val mockBalance = mockk<Point>(relaxed = true)
                val mockPayment = mockk<PaymentDto>()
                
                every { mockReservation.userId } returns userId
                every { mockReservation.seatId } returns seatId
                every { mockReservation.isTemporary() } returns true
                every { mockReservation.isExpired() } returns false
                every { mockSeat.price } returns paymentAmount
                every { mockBalance.amount } returns BigDecimal("100000")
                every { mockPayment.paymentId } returns 1L
                every { mockPayment.amount } returns paymentAmount
                
                every { tokenLifecycleManager.findToken(token) } returns mockToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns mockStatus
                justRun { tokenDomainService.validateActiveToken(mockToken, mockStatus) }
                
                every { reservationService.getReservationById(reservationId) } returns mockReservation
                every { seatService.getSeatById(seatId) } returns mockSeat
                every { paymentService.createPayment(userId, paymentAmount) } returns mockPayment
                every { balanceService.getBalance(userId) } returns mockBalance
                justRun { paymentService.validatePaymentAmount(mockBalance.amount, mockPayment.amount) }
                every { deductBalanceUseCase.execute(userId, paymentAmount) } throws RuntimeException("잔액 부족")
                justRun { paymentService.failPayment(mockPayment.paymentId, reservationId, "잔액 부족", token) }

                // when & then
                shouldThrow<PaymentProcessException> {
                    processPaymentUseCase.execute(userId, reservationId, token)
                }
                
                verify { paymentService.failPayment(mockPayment.paymentId, reservationId, "잔액 부족", token) }
            }
        }
    }
})
