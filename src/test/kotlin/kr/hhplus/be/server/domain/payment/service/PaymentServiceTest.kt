package kr.hhplus.be.server.domain.payment.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.justRun
import io.mockk.verify
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import kr.hhplus.be.server.domain.payment.repository.PaymentStatusTypePojoRepository
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentServiceTest : DescribeSpec({

    val paymentRepository = mockk<PaymentRepository>()
    val paymentStatusTypePojoRepository = mockk<PaymentStatusTypePojoRepository>()
    val domainEventPublisher = mockk<DomainEventPublisher>()

    val paymentService = PaymentService(
        paymentRepository, 
        paymentStatusTypePojoRepository,
        domainEventPublisher
    )
    
    // PaymentStatusType mock 설정
    fun setupPaymentStatusTypeMocks() {
        val now = LocalDateTime.now()
        every { paymentStatusTypePojoRepository.getPendingStatus() } returns PaymentStatusType("PEND", "대기", "결제 대기", true, now)
        every { paymentStatusTypePojoRepository.getCompletedStatus() } returns PaymentStatusType("COMP", "완료", "결제 완료", true, now)
        every { paymentStatusTypePojoRepository.getFailedStatus() } returns PaymentStatusType("FAIL", "실패", "결제 실패", true, now)
    }
    
    // EventPublisher mock 설정
    fun setupEventPublisherMock() {
        justRun { domainEventPublisher.publish(any()) }
    }
    
    describe("createPayment") {
        context("유효한 사용자와 금액으로 결제를 생성할 때") {
            it("결제를 성공적으로 생성해야 한다") {
                // given
                val userId = 1L
                val amount = BigDecimal("50000")
                val pendingStatus = PaymentStatusType("PEND", "대기", "결제 대기", true, LocalDateTime.now())
                val payment = Payment.create(userId, amount, "POINT", pendingStatus)
                
                setupPaymentStatusTypeMocks()
                every { paymentRepository.save(any()) } returns payment
                
                // when
                val result = paymentService.createPayment(userId, amount)
                
                // then
                result shouldNotBe null
                verify { paymentRepository.save(any()) }
            }
        }
    }
    
    describe("validatePaymentAmount") {
        context("충분한 잔액이 있을 때") {
            it("검증이 성공해야 한다") {
                // given
                val currentBalance = BigDecimal("100000")
                val paymentAmount = BigDecimal("50000")
                
                // when & then (예외가 발생하지 않아야 함)
                paymentService.validatePaymentAmount(currentBalance, paymentAmount)
            }
        }
        
        context("잔액이 부족할 때") {
            it("PaymentProcessException을 던져야 한다") {
                // given
                val currentBalance = BigDecimal("30000")
                val paymentAmount = BigDecimal("50000")
                
                // when & then
                shouldThrow<PaymentProcessException> {
                    paymentService.validatePaymentAmount(currentBalance, paymentAmount)
                }
            }
        }
    }
    
    describe("completePayment") {
        context("유효한 결제를 완료할 때") {
            it("결제를 성공적으로 완료하고 이벤트를 발행해야 한다") {
                // given
                val paymentId = 1L
                val reservationId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                val pendingStatus = PaymentStatusType("PEND", "대기", "결제 대기", true, LocalDateTime.now())
                val completedStatus = PaymentStatusType("COMP", "완료", "결제 완료", true, LocalDateTime.now())
                val payment = Payment.create(1L, BigDecimal("50000"), "POINT", pendingStatus)
                val completedPayment = payment.complete(completedStatus)
                
                setupPaymentStatusTypeMocks()
                setupEventPublisherMock()
                every { paymentRepository.findById(paymentId) } returns payment
                every { paymentRepository.save(any()) } returns completedPayment
                
                // when
                val result = paymentService.completePayment(paymentId, reservationId, seatId, token)
                
                // then
                result shouldNotBe null
                verify { paymentRepository.save(any()) }
                verify { domainEventPublisher.publish(any()) }
            }
        }
        
        context("존재하지 않는 결제를 완료하려 할 때") {
            it("PaymentNotFoundException을 던져야 한다") {
                // given
                val paymentId = 999L
                val reservationId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                every { paymentRepository.findById(paymentId) } returns null
                
                // when & then
                shouldThrow<PaymentNotFoundException> {
                    paymentService.completePayment(paymentId, reservationId, seatId, token)
                }
            }
        }
    }
    
    describe("failPayment") {
        context("결제를 실패 처리할 때") {
            it("결제를 실패 상태로 변경하고 이벤트를 발행해야 한다") {
                // given
                val paymentId = 1L
                val reservationId = 1L
                val reason = "잔액 부족"
                val token = "valid-token"
                
                val pendingStatus = PaymentStatusType("PEND", "대기", "결제 대기", true, LocalDateTime.now())
                val failedStatus = PaymentStatusType("FAIL", "실패", "결제 실패", true, LocalDateTime.now())
                val payment = Payment.create(1L, BigDecimal("50000"), "POINT", pendingStatus)
                val failedPayment = payment.fail(failedStatus)
                
                setupPaymentStatusTypeMocks()
                setupEventPublisherMock()
                every { paymentRepository.findById(paymentId) } returns payment
                every { paymentRepository.save(any()) } returns failedPayment
                
                // when
                val result = paymentService.failPayment(paymentId, reservationId, reason, token)
                
                // then
                result shouldNotBe null
                verify { paymentRepository.save(any()) }
                verify { domainEventPublisher.publish(any()) }
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