package kr.hhplus.be.server.payment.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.service.BalanceService
import kr.hhplus.be.server.concert.dto.SeatDto
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.payment.exception.PaymentAlreadyProcessedException
import kr.hhplus.be.server.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.payment.exception.PaymentProcessException
import kr.hhplus.be.server.payment.repository.PaymentRepository
import kr.hhplus.be.server.reservation.entity.Reservation
import kr.hhplus.be.server.reservation.service.ReservationService
import kr.hhplus.be.server.user.exception.UserNotFoundException
import kr.hhplus.be.server.user.service.UserService
import java.math.BigDecimal
import java.util.*

class PaymentServiceTest : DescribeSpec({
    
    val paymentRepository = mockk<PaymentRepository>()
    val reservationService = mockk<ReservationService>()
    val balanceService = mockk<BalanceService>()
    val tokenService = mockk<TokenService>()
    val seatService = mockk<SeatService>()
    val userService = mockk<UserService>()
    
    val paymentService = PaymentService(
        paymentRepository, reservationService, balanceService, 
        tokenService, seatService, userService
    )
    
    describe("processPayment") {
        context("모든 조건이 유효할 때") {
            it("결제를 성공적으로 처리해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val paymentAmount = BigDecimal("50000")
                
                val reservation = mockk<Reservation>(relaxed = true)
                val seat = mockk<SeatDto>(relaxed = true)
                val currentBalance = Point.create(userId, BigDecimal("100000"))
                val payment = Payment.create(userId, paymentAmount)
                val completedPayment = payment.complete()
                
                every { tokenService.validateActiveToken(token) } returns mockk(relaxed = true)
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { reservation.userId } returns userId
                every { reservation.isTemporary() } returns true
                every { reservation.isExpired() } returns false
                every { reservation.seatId } returns seatId
                every { seatService.getSeatById(seatId) } returns seat
                every { seat.price } returns paymentAmount
                every { balanceService.getBalance(userId) } returns currentBalance
                every { paymentRepository.save(any()) } returnsMany listOf(payment, completedPayment)
                every { balanceService.deductBalance(userId, paymentAmount) } returns mockk(relaxed = true)
                every { reservationService.confirmReservation(reservationId, payment.paymentId) } returns mockk(relaxed = true)
                every { seatService.confirmSeat(seatId) } returns mockk(relaxed = true)
                every { tokenService.completeReservation(token) } returns Unit
                
                // when
                val result = paymentService.processPayment(userId, reservationId, token)
                
                // then
                result shouldNotBe null
            }
        }
        
        context("유효하지 않은 토큰으로 결제할 때") {
            it("토큰 검증에서 예외가 발생해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "invalid-token"
                
                every { tokenService.validateActiveToken(token) } throws RuntimeException("Invalid token")
                
                // when & then
                shouldThrow<RuntimeException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }
        
        context("존재하지 않는 사용자가 결제할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                val reservationId = 1L
                val token = "valid-token"
                
                every { tokenService.validateActiveToken(token) } returns mockk(relaxed = true)
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }
        
        context("다른 사용자의 예약으로 결제할 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val reservation = mockk<Reservation>(relaxed = true)
                
                every { tokenService.validateActiveToken(token) } returns mockk(relaxed = true)
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { reservation.userId } returns 2L // 다른 사용자
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }
        
        context("임시 예약 상태가 아닌 예약으로 결제할 때") {
            it("PaymentProcessException 던져야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val reservation = mockk<Reservation>(relaxed = true)
                
                every { tokenService.validateActiveToken(token) } returns mockk(relaxed = true)
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { reservation.userId } returns userId
                every { reservation.isTemporary() } returns false
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }
        
        context("만료된 예약으로 결제할 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val reservation = mockk<Reservation>(relaxed = true)
                
                every { tokenService.validateActiveToken(token) } returns mockk(relaxed = true)
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { reservation.userId } returns userId
                every { reservation.isTemporary() } returns true
                every { reservation.isExpired() } returns true
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }
        
        context("좌석 정보가 없는 예약으로 결제할 때") {
            it("PaymentAlreadyProcessedException을 던져야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val reservation = mockk<Reservation>(relaxed = true)
                
                every { tokenService.validateActiveToken(token) } returns mockk(relaxed = true)
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { reservation.userId } returns userId
                every { reservation.isTemporary() } returns true
                every { reservation.isExpired() } returns false
                every { reservation.seatId } returns 1L
                
                // when & then
                shouldThrow<PaymentAlreadyProcessedException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }
        
        context("잔액이 부족할 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val paymentAmount = BigDecimal("50000")
                
                val reservation = mockk<Reservation>(relaxed = true)
                val seat = mockk<SeatDto>(relaxed = true)
                val currentBalance = Point.create(userId, BigDecimal("10000")) // 부족한 잔액
                
                every { tokenService.validateActiveToken(token) } returns mockk(relaxed = true)
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { reservation.userId } returns userId
                every { reservation.isTemporary() } returns true
                every { reservation.isExpired() } returns false
                every { reservation.seatId } returns seatId
                every { seatService.getSeatById(seatId) } returns seat
                every { seat.price } returns paymentAmount
                every { balanceService.getBalance(userId) } returns currentBalance
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }
    }
    
    describe("getPaymentById") {
        context("존재하는 결제 ID로 조회할 때") {
            it("해당 결제 정보를 반환해야 한다") {
                // given
                val paymentId = 1L
                val payment = Payment.create(1L, BigDecimal("50000"))
                
                every { paymentRepository.findById(paymentId) } returns Optional.of(payment)
                
                // when
                val result = paymentService.getPaymentById(paymentId)
                
                // then
                result shouldNotBe null
            }
        }
        
        context("존재하지 않는 결제 ID로 조회할 때") {
            it("PaymentNotFoundException을 던져야 한다") {
                // given
                val paymentId = 999L
                
                every { paymentRepository.findById(paymentId) } returns Optional.empty()
                
                // when & then
                shouldThrow<PaymentNotFoundException> {
                    paymentService.getPaymentById(paymentId)
                }
            }
        }
    }
})
