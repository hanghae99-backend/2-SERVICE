package kr.hhplus.be.server.payment.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import kr.hhplus.be.server.auth.entity.WaitingToken
import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.service.BalanceService
import kr.hhplus.be.server.concert.entity.SeatInfo
import kr.hhplus.be.server.concert.entity.SeatStatus
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.payment.entity.PaymentStatus
import kr.hhplus.be.server.payment.entity.PaymentProcessException
import kr.hhplus.be.server.payment.entity.Reservation
import kr.hhplus.be.server.payment.repository.PaymentRepository
import kr.hhplus.be.server.payment.repository.ReservationRepository
import kr.hhplus.be.server.user.service.UserService
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class PaymentServiceUnitTest : BehaviorSpec({
    lateinit var tokenService: TokenService
    lateinit var userService: UserService
    lateinit var reservationRepository: ReservationRepository
    lateinit var paymentRepository: PaymentRepository
    lateinit var balanceService: BalanceService
    lateinit var seatService: SeatService
    lateinit var paymentService: PaymentService

    beforeTest {
        tokenService = mockk()
        userService = mockk()
        reservationRepository = mockk()
        paymentRepository = mockk()
        balanceService = mockk()
        seatService = mockk()
        
        paymentService = PaymentService(
            tokenService = tokenService,
            userService = userService,
            reservationRepository = reservationRepository,
            paymentRepository = paymentRepository,
            balanceService = balanceService,
            seatService = seatService
        )
        
        clearMocks(tokenService, userService, reservationRepository, paymentRepository, balanceService, seatService)
    }

    Given("PaymentService") {
        When("유효한 결제 요청이 들어오면") {
            Then("결제가 성공적으로 처리된다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val paymentAmount = BigDecimal("150000")
                
                val reservation = Reservation.create(userId, seatId, LocalDateTime.now().plusMinutes(5))
                val seat = SeatInfo(seatId, 1, paymentAmount, SeatStatus.RESERVED)
                val payment = Payment.create(userId, reservationId, paymentAmount)
                val point = Point.create(1, BigDecimal.ONE)
                val completedPayment = payment.complete()
                val confirmedReservation = reservation.confirm()
                val mockToken = WaitingToken(token, userId)
                
                // Mock 설정
                every { tokenService.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationRepository.findById(reservationId) } returns Optional.of(reservation)
                every { paymentRepository.findByReservationId(reservationId) } returns null
                every { seatService.getSeatById(seatId) } returns seat
                every { balanceService.checkBalance(userId, paymentAmount) } returns true
                every { paymentRepository.save(any<Payment>()) } returns payment andThen completedPayment
                every { balanceService.deductBalance(userId, paymentAmount) } returns point
                every { reservationRepository.save(any<Reservation>()) } returns confirmedReservation
                every { seatService.updateSeatStatus(seatId, SeatStatus.CONFIRMED) } returns true
                every { tokenService.completeReservation(token) } returns Unit
                
                // when
                val result = paymentService.processPayment(userId, reservationId, token)
                
                // then
                result.shouldNotBeNull()
                result.status shouldBe PaymentStatus.COMPLETED
                
                verify {
                    tokenService.validateActiveToken(token)
                    userService.existsById(userId)
                    reservationRepository.findById(reservationId)
                    paymentRepository.findByReservationId(reservationId)
                    seatService.getSeatById(seatId)
                    balanceService.checkBalance(userId, paymentAmount)
                    paymentRepository.save(any<Payment>())
                    balanceService.deductBalance(userId, paymentAmount)
                    reservationRepository.save(any<Reservation>())
                    seatService.updateSeatStatus(seatId, SeatStatus.CONFIRMED)
                    tokenService.completeReservation(token)
                }
            }
        }

        When("존재하지 않는 사용자로 결제 요청하면") {
            Then("PaymentProcessException이 발생한다") {
                // given
                val nonExistentUserId = 999L
                val reservationId = 1L
                val token = "valid-token"
                val mockToken = WaitingToken(token, nonExistentUserId)
                
                every { tokenService.validateActiveToken(token) } returns mockToken
                every { userService.existsById(nonExistentUserId) } returns false
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(nonExistentUserId, reservationId, token)
                }
                
                verify { tokenService.validateActiveToken(token) }
                verify { userService.existsById(nonExistentUserId) }
                verify(exactly = 0) { reservationRepository.findById(any()) }
            }
        }

        When("존재하지 않는 예약으로 결제 요청하면") {
            Then("PaymentProcessException이 발생한다") {
                // given
                val userId = 1L
                val nonExistentReservationId = 999L
                val token = "valid-token"
                val mockToken = WaitingToken(token, userId)
                
                every { tokenService.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationRepository.findById(nonExistentReservationId) } returns Optional.empty()
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, nonExistentReservationId, token)
                }
                
                verify { tokenService.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { reservationRepository.findById(nonExistentReservationId) }
            }
        }

        When("다른 사용자의 예약으로 결제 요청하면") {
            Then("PaymentProcessException이 발생한다") {
                // given
                val userId = 1L
                val otherUserId = 2L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val mockToken = WaitingToken(token, userId)
                
                val otherUserReservation = Reservation.create(otherUserId, seatId, LocalDateTime.now().plusMinutes(5))
                
                every { tokenService.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationRepository.findById(reservationId) } returns Optional.of(otherUserReservation)
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
                
                verify { tokenService.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { reservationRepository.findById(reservationId) }
            }
        }

        When("이미 결제된 예약으로 재결제 요청하면") {
            Then("PaymentProcessException이 발생한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val mockToken = WaitingToken(token, userId)
                
                val reservation = Reservation.create(userId, seatId, LocalDateTime.now().plusMinutes(5))
                val existingPayment = Payment.create(userId, reservationId, BigDecimal("150000")).complete()
                
                every { tokenService.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationRepository.findById(reservationId) } returns Optional.of(reservation)
                every { paymentRepository.findByReservationId(reservationId) } returns existingPayment
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
                
                verify { tokenService.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { reservationRepository.findById(reservationId) }
                verify { paymentRepository.findByReservationId(reservationId) }
            }
        }

        When("잔액 부족으로 결제 요청하면") {
            Then("PaymentProcessException이 발생한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val paymentAmount = BigDecimal("150000")
                val mockToken = WaitingToken(token, userId)
                
                val reservation = Reservation.create(userId, seatId, LocalDateTime.now().plusMinutes(5))
                val seat = SeatInfo(seatId, 1, paymentAmount, SeatStatus.RESERVED)
                
                every { tokenService.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationRepository.findById(reservationId) } returns Optional.of(reservation)
                every { paymentRepository.findByReservationId(reservationId) } returns null
                every { seatService.getSeatById(seatId) } returns seat
                every { balanceService.checkBalance(userId, paymentAmount) } returns false
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }

        When("만료된 예약으로 결제 요청하면") {
            Then("PaymentProcessException이 발생한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val paymentAmount = BigDecimal("150000")
                val mockToken = WaitingToken(token, userId)
                
                val expiredReservation = Reservation.create(userId, seatId, LocalDateTime.now().minusMinutes(10))
                val seat = SeatInfo(seatId, 1, paymentAmount, SeatStatus.RESERVED)
                
                every { tokenService.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationRepository.findById(reservationId) } returns Optional.of(expiredReservation)
                every { paymentRepository.findByReservationId(reservationId) } returns null
                every { seatService.getSeatById(seatId) } returns seat
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.processPayment(userId, reservationId, token)
                }
            }
        }
    }
})
