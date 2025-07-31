package kr.hhplus.be.server.api.payment.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.justRun
import io.mockk.verify
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.api.auth.usecase.TokenUseCase
import kr.hhplus.be.server.api.balance.usecase.BalanceUseCase
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import java.math.BigDecimal
import java.time.LocalDateTime
import io.mockk.spyk

class PaymentUseCaseTest : DescribeSpec({

    val paymentService = mockk<PaymentService>()
    val reservationService = mockk<ReservationService>()
    val seatService = mockk<SeatService>()
    val userService = mockk<UserService>()
    val balanceUseCase = mockk<BalanceUseCase>()
    val tokenUseCase = mockk<TokenUseCase>()
    val distributedLock = mockk<DistributedLock>(relaxed = true)

    val paymentUseCase = PaymentUseCase(
        paymentService,
        reservationService,
        seatService,
        userService,
        balanceUseCase,
        tokenUseCase,
        distributedLock
    )
    
    // DistributedLock executeWithLock 메서드의 기본 동작 설정
    fun <T> setupDistributedLockMock(distributedLock: DistributedLock) {
        every {
            distributedLock.executeWithLock<T>(
                lockKey = any(),
                lockTimeoutMs = any(),
                waitTimeoutMs = any(),
                action = any()
            )
        } answers {
            val action = args[3] as () -> T
            action.invoke()
        }
    }
    
    describe("processPayment") {
        context("유효한 결제 요청일 때") {
            it("결제를 성공적으로 처리해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val seatId = 1L
                val paymentAmount = BigDecimal("100000")
                
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시", "임시 예약", true, LocalDateTime.now())
                val reservation = Reservation.createTemporary(
                    userId = userId,
                    concertId = 1L,
                    seatId = seatId,
                    seatNumber = "A1",
                    price = paymentAmount,
                    temporaryStatus = temporaryStatus
                )
                
                val seat = SeatDto(
                    seatId = seatId,
                    scheduleId = 1L,
                    seatNumber = "A1",
                    price = paymentAmount,
                    statusCode = "AVAILABLE"
                )
                
                val currentBalance = Point.create(userId, BigDecimal("150000"))
                val payment = PaymentDto(
                    paymentId = 1L,
                    userId = userId,
                    amount = paymentAmount,
                    paymentMethod = "POINT",
                    statusCode = "PENDING",
                    paidAt = LocalDateTime.now(),
                    reservationList = emptyList(),
                )
                val completedPayment = payment.copy(
                    statusCode = "COMPLETED"
                )
                
                val mockToken = WaitingToken.create(token, userId)
                
                // DistributedLock mocking - 단순화된 방식
                every {
                    distributedLock.executeWithLock<PaymentDto>(any(), any(), any(), any())
                } answers {
                    val action = args[3] as () -> PaymentDto
                    action.invoke()
                }
                
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { seatService.getSeatById(seatId) } returns seat
                every { paymentService.createPayment(userId, paymentAmount) } returns payment
                every { balanceUseCase.getBalance(userId) } returns currentBalance
                justRun { paymentService.validatePaymentAmount(any(), any()) }
                every { balanceUseCase.deductBalanceInternal(userId, payment.amount) } returns currentBalance.deduct(payment.amount)
                every { reservationService.confirmReservationInternal(reservationId, payment.paymentId) } returns reservation
                every { seatService.confirmSeatInternal(seatId) } returns seat
                every { paymentService.completePayment(payment.paymentId, reservationId, seatId, token) } returns completedPayment
                
                // when
                val result = paymentUseCase.processPayment(userId, reservationId, token)
                
                // then
                result shouldNotBe null
                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { paymentService.createPayment(userId, paymentAmount) }
                verify { balanceUseCase.deductBalanceInternal(userId, payment.amount) }
                verify { paymentService.completePayment(payment.paymentId, reservationId, seatId, token) }
            }
        }
        
        context("존재하지 않는 사용자가 결제할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                val reservationId = 1L
                val token = "valid-token"
                
                val mockToken = WaitingToken.create(token, userId)
                
                setupDistributedLockMock<PaymentDto>(distributedLock)
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    paymentUseCase.processPayment(userId, reservationId, token)
                }
                
                verify { userService.existsById(userId) }
            }
        }
        
        context("다른 사용자의 예약으로 결제할 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val userId = 1L
                val otherUserId = 2L
                val reservationId = 1L
                val token = "valid-token"
                
                val mockToken = WaitingToken.create(token, userId)
                
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시", "임시 예약", true, LocalDateTime.now())
                val reservation = Reservation.createTemporary(
                    userId = otherUserId, // 다른 사용자의 예약
                    concertId = 1L,
                    seatId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("100000"),
                    temporaryStatus = temporaryStatus
                )
                
                setupDistributedLockMock<PaymentDto>(distributedLock)
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentUseCase.processPayment(userId, reservationId, token)
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
                val paymentAmount = BigDecimal("100000")
                
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시", "임시 예약", true, LocalDateTime.now())
                val reservation = Reservation.createTemporary(
                    userId = userId,
                    concertId = 1L,
                    seatId = seatId,
                    seatNumber = "A1",
                    price = paymentAmount,
                    temporaryStatus = temporaryStatus
                )
                
                val seat = SeatDto(
                    seatId = seatId,
                    scheduleId = 1L,
                    seatNumber = "A1",
                    price = paymentAmount,
                    statusCode = "AVAILABLE"
                )
                
                val insufficientBalance = Point.create(userId, BigDecimal("50000")) // 잔액 부족
                val payment = PaymentDto(
                    paymentId = 1L,
                    userId = userId,
                    amount = paymentAmount,
                    paymentMethod = "POINT",
                    statusCode = "PENDING",
                    paidAt = LocalDateTime.now(),
                    reservationList = emptyList(),
                )
                val failedPayment = payment.copy(
                    statusCode = "FAILED",
                )
                
                val mockToken = WaitingToken.create(token, userId)
                
                setupDistributedLockMock<PaymentDto>(distributedLock)
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationService.getReservationById(reservationId) } returns reservation
                every { seatService.getSeatById(seatId) } returns seat
                every { paymentService.createPayment(userId, paymentAmount) } returns payment
                every { balanceUseCase.getBalance(userId) } returns insufficientBalance
                every { paymentService.validatePaymentAmount(any(), any()) } throws PaymentProcessException("잔액 부족")
                every { paymentService.failPayment(any(), any(), any(), any()) } returns failedPayment
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentUseCase.processPayment(userId, reservationId, token)
                }
                
                verify { paymentService.failPayment(payment.paymentId, reservationId, any(), token) }
            }
            context("토큰이 비활성화 상태일 때") {
                it("TokenActivationException을 던져야 한다") {
                    // given
                    val userId = 1L
                    val reservationId = 1L
                    val token = "inactive-token"

                    setupDistributedLockMock<PaymentDto>(distributedLock)
                    every { tokenUseCase.validateActiveToken(token) } throws TokenActivationException("토큰이 비활성화 상태입니다")

                    // when & then
                    shouldThrow<TokenActivationException> {
                        paymentUseCase.processPayment(userId, reservationId, token)
                    }

                    verify { tokenUseCase.validateActiveToken(token) }
                }
            }

            context("이미 확정된 예약으로 결제할 때") {
                it("PaymentProcessException을 던져야 한다") {
                    // given
                    val userId = 1L
                    val reservationId = 1L
                    val token = "valid-token"
                    val seatId = 1L

                    val confirmedStatus = ReservationStatusType("CONFIRMED", "확정", "확정된 예약", true, LocalDateTime.now())
                    val reservation = spyk(Reservation.createTemporary(
                        userId = userId,
                        concertId = 1L,
                        seatId = seatId,
                        seatNumber = "A1",
                        price = BigDecimal("100000"),
                        temporaryStatus = confirmedStatus
                    ))

                    val mockToken = WaitingToken.create(token, userId)

                    setupDistributedLockMock<PaymentDto>(distributedLock)
                    every { tokenUseCase.validateActiveToken(token) } returns mockToken
                    every { userService.existsById(userId) } returns true
                    every { reservationService.getReservationById(reservationId) } returns reservation
                    every { reservation.isTemporary() } returns false

                    // when & then
                    shouldThrow<PaymentProcessException> {
                        paymentUseCase.processPayment(userId, reservationId, token)
                    }
                }
            }

            context("만료된 예약으로 결제할 때") {
                it("PaymentProcessException을 던져야 한다") {
                    // given
                    val userId = 1L
                    val reservationId = 1L
                    val token = "valid-token"
                    val seatId = 1L
                    val paymentAmount = BigDecimal("100000")

                    val temporaryStatus = ReservationStatusType("TEMPORARY", "임시", "임시 예약", true, LocalDateTime.now())
                    val reservation = mockk<Reservation>()
                    every { reservation.userId } returns userId
                    every { reservation.seatId } returns seatId
                    every { reservation.isTemporary() } returns true
                    every { reservation.isExpired() } returns true

                    val mockToken = WaitingToken.create(token, userId)

                    setupDistributedLockMock<PaymentDto>(distributedLock)
                    every { tokenUseCase.validateActiveToken(token) } returns mockToken
                    every { userService.existsById(userId) } returns true
                    every { reservationService.getReservationById(reservationId) } returns reservation

                    // when & then
                    shouldThrow<PaymentProcessException> {
                        paymentUseCase.processPayment(userId, reservationId, token)
                    }
                }
            }

            context("좌석 조회 실패시") {
                it("좌석 서비스에서 예외를 던져야 한다") {
                    // given
                    val userId = 1L
                    val reservationId = 1L
                    val token = "valid-token"
                    val seatId = 1L
                    val paymentAmount = BigDecimal("100000")

                    val temporaryStatus = ReservationStatusType("TEMPORARY", "임시", "임시 예약", true, LocalDateTime.now())
                    val reservation = Reservation.createTemporary(
                        userId = userId,
                        concertId = 1L,
                        seatId = seatId,
                        seatNumber = "A1",
                        price = paymentAmount,
                        temporaryStatus = temporaryStatus
                    )

                    val mockToken = WaitingToken.create(token, userId)

                    setupDistributedLockMock<PaymentDto>(distributedLock)
                    every { tokenUseCase.validateActiveToken(token) } returns mockToken
                    every { userService.existsById(userId) } returns true
                    every { reservationService.getReservationById(reservationId) } returns reservation
                    every { seatService.getSeatById(seatId) } throws RuntimeException("좌석을 찾을 수 없습니다")

                    // when & then
                    shouldThrow<RuntimeException> {
                        paymentUseCase.processPayment(userId, reservationId, token)
                    }
                }
            }

            context("결제 생성 실패시") {
                it("결제 서비스에서 예외를 던져야 한다") {
                    // given
                    val userId = 1L
                    val reservationId = 1L
                    val token = "valid-token"
                    val seatId = 1L
                    val paymentAmount = BigDecimal("100000")

                    val temporaryStatus = ReservationStatusType("TEMPORARY", "임시", "임시 예약", true, LocalDateTime.now())
                    val reservation = Reservation.createTemporary(
                        userId = userId,
                        concertId = 1L,
                        seatId = seatId,
                        seatNumber = "A1",
                        price = paymentAmount,
                        temporaryStatus = temporaryStatus
                    )

                    val seat = SeatDto(
                        seatId = seatId,
                        scheduleId = 1L,
                        seatNumber = "A1",
                        price = paymentAmount,
                        statusCode = "AVAILABLE"
                    )

                    val mockToken = WaitingToken.create(token, userId)

                    setupDistributedLockMock<PaymentDto>(distributedLock)
                    every { tokenUseCase.validateActiveToken(token) } returns mockToken
                    every { userService.existsById(userId) } returns true
                    every { reservationService.getReservationById(reservationId) } returns reservation
                    every { seatService.getSeatById(seatId) } returns seat
                    every { paymentService.createPayment(userId, paymentAmount) } throws RuntimeException("결제 생성 실패")

                    // when & then
                    shouldThrow<RuntimeException> {
                        paymentUseCase.processPayment(userId, reservationId, token)
                    }
                }
            }

            context("예약 확정 실패시") {
                it("실패한 결제로 처리하고 예외를 던져야 한다") {
                    // given
                    val userId = 1L
                    val reservationId = 1L
                    val token = "valid-token"
                    val seatId = 1L
                    val paymentAmount = BigDecimal("100000")

                    val temporaryStatus = ReservationStatusType("TEMPORARY", "임시", "임시 예약", true, LocalDateTime.now())
                    val reservation = Reservation.createTemporary(
                        userId = userId,
                        concertId = 1L,
                        seatId = seatId,
                        seatNumber = "A1",
                        price = paymentAmount,
                        temporaryStatus = temporaryStatus
                    )

                    val seat = SeatDto(
                        seatId = seatId,
                        scheduleId = 1L,
                        seatNumber = "A1",
                        price = paymentAmount,
                        statusCode = "AVAILABLE"
                    )

                    val currentBalance = Point.create(userId, BigDecimal("150000"))
                    val payment = PaymentDto(
                        paymentId = 1L,
                        userId = userId,
                        amount = paymentAmount,
                        paymentMethod = "POINT",
                        statusCode = "PENDING",
                        paidAt = LocalDateTime.now(),
                        reservationList = emptyList(),
                    )
                    val failedPayment = payment.copy(statusCode = "FAILED")

                    val mockToken = WaitingToken.create(token, userId)

                    setupDistributedLockMock<PaymentDto>(distributedLock)
                    every { tokenUseCase.validateActiveToken(token) } returns mockToken
                    every { userService.existsById(userId) } returns true
                    every { reservationService.getReservationById(reservationId) } returns reservation
                    every { seatService.getSeatById(seatId) } returns seat
                    every { paymentService.createPayment(userId, paymentAmount) } returns payment
                    every { balanceUseCase.getBalance(userId) } returns currentBalance
                    justRun { paymentService.validatePaymentAmount(any(), any()) }
                    every { balanceUseCase.deductBalanceInternal(userId, payment.amount) } returns currentBalance.deduct(payment.amount)
                    every { reservationService.confirmReservationInternal(reservationId, payment.paymentId) } throws RuntimeException("예약 확정 실패")
                    every { paymentService.failPayment(any(), any(), any(), any()) } returns failedPayment

                    // when & then
                    shouldThrow<PaymentProcessException> {
                        paymentUseCase.processPayment(userId, reservationId, token)
                    }

                    verify { paymentService.failPayment(payment.paymentId, reservationId, any(), token) }
                }
            }

            context("좌석 확정 실패시") {
                it("실패한 결제로 처리하고 예외를 던져야 한다") {
                    // given
                    val userId = 1L
                    val reservationId = 1L
                    val token = "valid-token"
                    val seatId = 1L
                    val paymentAmount = BigDecimal("100000")

                    val temporaryStatus = ReservationStatusType("TEMPORARY", "임시", "임시 예약", true, LocalDateTime.now())
                    val reservation = Reservation.createTemporary(
                        userId = userId,
                        concertId = 1L,
                        seatId = seatId,
                        seatNumber = "A1",
                        price = paymentAmount,
                        temporaryStatus = temporaryStatus
                    )

                    val seat = SeatDto(
                        seatId = seatId,
                        scheduleId = 1L,
                        seatNumber = "A1",
                        price = paymentAmount,
                        statusCode = "AVAILABLE"
                    )

                    val currentBalance = Point.create(userId, BigDecimal("150000"))
                    val payment = PaymentDto(
                        paymentId = 1L,
                        userId = userId,
                        amount = paymentAmount,
                        paymentMethod = "POINT",
                        statusCode = "PENDING",
                        paidAt = LocalDateTime.now(),
                        reservationList = emptyList(),
                    )
                    val failedPayment = payment.copy(statusCode = "FAILED")

                    val mockToken = WaitingToken.create(token, userId)

                    setupDistributedLockMock<PaymentDto>(distributedLock)
                    every { tokenUseCase.validateActiveToken(token) } returns mockToken
                    every { userService.existsById(userId) } returns true
                    every { reservationService.getReservationById(reservationId) } returns reservation
                    every { seatService.getSeatById(seatId) } returns seat
                    every { paymentService.createPayment(userId, paymentAmount) } returns payment
                    every { balanceUseCase.getBalance(userId) } returns currentBalance
                    justRun { paymentService.validatePaymentAmount(any(), any()) }
                    every { balanceUseCase.deductBalanceInternal(userId, payment.amount) } returns currentBalance.deduct(payment.amount)
                    every { reservationService.confirmReservationInternal(reservationId, payment.paymentId) } returns reservation
                    every { seatService.confirmSeatInternal(seatId) } throws RuntimeException("좌석 확정 실패")
                    every { paymentService.failPayment(any(), any(), any(), any()) } returns failedPayment

                    // when & then
                    shouldThrow<PaymentProcessException> {
                        paymentUseCase.processPayment(userId, reservationId, token)
                    }

                    verify { paymentService.failPayment(payment.paymentId, reservationId, any(), token) }
                }
            }
        }

        describe("getPaymentById - 추가 케이스") {
            context("존재하지 않는 결제 ID로 조회할 때") {
                it("PaymentNotFoundException을 던져야 한다") {
                    // given
                    val paymentId = 999L

                    every { paymentService.getPaymentById(paymentId) } throws PaymentNotFoundException("결제를 찾을 수 없습니다")

                    // when & then
                    shouldThrow<PaymentNotFoundException> {
                        paymentUseCase.getPaymentById(paymentId)
                    }

                    verify { paymentService.getPaymentById(paymentId) }
                }
            }

            context("음수 결제 ID로 조회할 때") {
                it("IllegalArgumentException을 던져야 한다") {
                    // given
                    val paymentId = -1L

                    every { paymentService.getPaymentById(paymentId) } throws IllegalArgumentException("잘못된 결제 ID입니다")

                    // when & then
                    shouldThrow<IllegalArgumentException> {
                        paymentUseCase.getPaymentById(paymentId)
                    }

                    verify { paymentService.getPaymentById(paymentId) }
                }
            }
        }
    }
    
    describe("getPaymentById") {
        context("존재하는 결제 ID로 조회할 때") {
            it("해당 결제 정보를 반환해야 한다") {
                // given
                val paymentId = 1L
                val payment = PaymentDto(
                    paymentId = paymentId,
                    userId = 1L,
                    amount = BigDecimal("100000"),
                    paymentMethod = "POINT",
                    statusCode = "COMPLETED",
                    paidAt = LocalDateTime.now(),
                    reservationList = emptyList(),
                )
                
                every { paymentService.getPaymentById(paymentId) } returns payment
                
                // when
                val result = paymentUseCase.getPaymentById(paymentId)
                
                // then
                result shouldNotBe null
                verify { paymentService.getPaymentById(paymentId) }
            }
        }
    }
})