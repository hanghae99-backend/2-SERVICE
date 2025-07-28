package kr.hhplus.be.server.payment.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.justRun
import kr.hhplus.be.server.domain.auth.service.TokenService
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import kr.hhplus.be.server.domain.payment.repository.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.user.service.UserService
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentServiceTest : DescribeSpec({

    val paymentRepository = mockk<PaymentRepository>()
    val paymentStatusTypePojoRepository = mockk<PaymentStatusTypePojoRepository>()
    val reservationService = mockk<ReservationService>()
    val balanceService = mockk<BalanceService>()
    val tokenService = mockk<TokenService>()
    val seatService = mockk<SeatService>()
    val userService = mockk<UserService>()
    val distributedLock = mockk<DistributedLock>()
    val eventPublisher = mockk<DomainEventPublisher>()

    val paymentService = PaymentService(
        paymentRepository, paymentStatusTypePojoRepository, reservationService, balanceService,
        tokenService, seatService, userService, distributedLock, eventPublisher
    )
    
    // DistributedLock executeWithLock 메서드의 기본 동작 설정
    fun setupDistributedLockMock() {
        every { 
            distributedLock.executeWithLock<Any>(
                lockKey = any(),
                lockTimeoutMs = any(),
                waitTimeoutMs = any(),
                action = any()
            )
        } answers {
            val action = args[3] as () -> Any
            action.invoke()
        }
    }
    
    // PaymentStatusType mock 설정
    fun setupPaymentStatusTypeMocks() {
        val now = LocalDateTime.now()
        every { paymentStatusTypePojoRepository.getPendingStatus() } returns PaymentStatusType("PENDING", "대기", "결제 대기", true, now)
        every { paymentStatusTypePojoRepository.getCompletedStatus() } returns PaymentStatusType("COMPLETED", "완료", "결제 완료", true, now)
        every { paymentStatusTypePojoRepository.getFailedStatus() } returns PaymentStatusType("FAILED", "실패", "결제 실패", true, now)
        every { paymentStatusTypePojoRepository.getCancelledStatus() } returns PaymentStatusType("CANCELLED", "취소", "결제 취소", true, now)
        every { paymentStatusTypePojoRepository.getRefundedStatus() } returns PaymentStatusType("REFUNDED", "환불", "결제 환불", true, now)
    }
    
    // EventPublisher mock 설정
    fun setupEventPublisherMock() {
        justRun { eventPublisher.publish(any()) }
    }
    
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
                
                val pendingStatus = PaymentStatusType("PEND", "대기", "결제 대기", true, LocalDateTime.now())
                val completedStatus = PaymentStatusType("COMP", "완료", "결제 완료", true, LocalDateTime.now())
                val payment = Payment.create(userId, paymentAmount, "POINT", pendingStatus)
                val completedPayment = payment.complete(completedStatus)
                
                setupDistributedLockMock()
                setupPaymentStatusTypeMocks()
                setupEventPublisherMock()
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
                every { balanceService.deductBalanceInternal(userId, paymentAmount) } returns mockk(relaxed = true)
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
                
                setupDistributedLockMock()
                setupPaymentStatusTypeMocks()
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
                
                setupDistributedLockMock()
                setupPaymentStatusTypeMocks()
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
                
                setupDistributedLockMock()
                setupPaymentStatusTypeMocks()
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
                
                setupDistributedLockMock()
                setupPaymentStatusTypeMocks()
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
                
                setupDistributedLockMock()
                setupPaymentStatusTypeMocks()
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
        
        context("존재하지 않는 좌석으로 결제할 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 999L // 존재하지 않는 좌석 ID
                val reservation = mockk<Reservation>(relaxed = true)
                
                setupDistributedLockMock()
                setupPaymentStatusTypeMocks()
                every { tokenService.validateActiveToken(token) } returns mockk(relaxed = true)
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { reservation.userId } returns userId
                every { reservation.isTemporary() } returns true
                every { reservation.isExpired() } returns false
                every { reservation.seatId } returns seatId
                every { seatService.getSeatById(seatId) } throws RuntimeException("좌석을 찾을 수 없습니다")
                
                // when & then
                shouldThrow<RuntimeException> {
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
                
                setupDistributedLockMock()
                setupPaymentStatusTypeMocks()
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
                val pendingStatus = PaymentStatusType("PEND", "대기", "결제 대기", true, LocalDateTime.now())
                val payment = Payment.create(1L, BigDecimal("50000"), "POINT", pendingStatus)
                
                setupPaymentStatusTypeMocks()
                every { paymentRepository.findById(paymentId) } returns payment
                
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
                
                every { paymentRepository.findById(paymentId) } returns null
                
                // when & then
                shouldThrow<PaymentNotFoundException> {
                    paymentService.getPaymentById(paymentId)
                }
            }
        }
    }
})
