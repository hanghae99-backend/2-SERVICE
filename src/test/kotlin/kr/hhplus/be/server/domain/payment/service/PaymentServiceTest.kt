package kr.hhplus.be.server.domain.payment.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.repositories.PaymentRepository
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.global.event.DomainEventPublisher
import java.math.BigDecimal

class PaymentServiceTest : DescribeSpec({
    
    val paymentRepository = mockk<PaymentRepository>()
    val paymentStatusTypeRepository = mockk<PaymentStatusTypePojoRepository>()
    val domainEventPublisher = mockk<DomainEventPublisher>()
    
    val paymentService = PaymentService(
        paymentRepository,
        paymentStatusTypeRepository,
        domainEventPublisher
    )
    
    beforeEach {
        every { domainEventPublisher.publish(any()) } returns Unit
    }
    
    describe("createReservationPayment") {
        context("유효한 예약에 대해 결제를 생성할 때") {
            it("결제가 성공적으로 생성되어야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val amount = BigDecimal("50000")
                
                val pendingStatus = PaymentStatusType.createDefault(PaymentStatusType.PENDING, "결제 대기", PaymentStatusType.CATEGORY_NORMAL, "결제 대기 상태")
                val payment = Payment.createForReservation(userId, reservationId, amount, "POINT", pendingStatus)
                payment.paymentId = 1L
                
                every { paymentStatusTypeRepository.getPendingStatus() } returns pendingStatus
                every { paymentRepository.save(any()) } returns payment
                
                // when
                val result = paymentService.createReservationPayment(userId, reservationId, amount)
                
                // then
                result shouldNotBe null
                result.userId shouldBe userId
                result.amount shouldBe amount
                verify { paymentRepository.save(any()) }
            }
        }
    }
    
    describe("completePayment") {
        context("유효한 결제를 완료할 때") {
            it("결제가 성공적으로 완료되어야 한다") {
                // given
                val paymentId = 1L
                val reservationId = 1L
                val seatId = 1L
                val token = "test-token"
                
                val pendingStatus = PaymentStatusType.createDefault(PaymentStatusType.PENDING, "결제 대기", PaymentStatusType.CATEGORY_NORMAL, "결제 대기 상태")
                val completedStatus = PaymentStatusType.createDefault(PaymentStatusType.COMPLETED, "결제 완료", PaymentStatusType.CATEGORY_NORMAL, "결제 완료 상태")
                val payment = Payment.createForReservation(1L, reservationId, BigDecimal("50000"), "POINT", pendingStatus)
                payment.paymentId = paymentId
                
                every { paymentRepository.findById(paymentId) } returns payment
                every { paymentStatusTypeRepository.getCompletedStatus() } returns completedStatus
                every { paymentRepository.save(any()) } returns payment
                
                // when
                val result = paymentService.completePayment(paymentId, reservationId, seatId, token)
                
                // then
                result shouldNotBe null
                result.paymentId shouldBe paymentId
                verify { paymentRepository.save(any()) }
                verify { domainEventPublisher.publish(any()) }
            }
        }
    }
    
    describe("getPaymentById") {
        context("존재하는 결제 ID로 조회할 때") {
            it("결제 정보를 반환해야 한다") {
                // given
                val paymentId = 1L
                val pendingStatus = PaymentStatusType.createDefault(PaymentStatusType.PENDING, "결제 대기", PaymentStatusType.CATEGORY_NORMAL, "결제 대기 상태")
                val payment = Payment.createForReservation(1L, 1L, BigDecimal("50000"), "POINT", pendingStatus)
                payment.paymentId = paymentId
                
                every { paymentRepository.findById(paymentId) } returns payment
                
                // when
                val result = paymentService.getPaymentById(paymentId)
                
                // then
                result shouldNotBe null
                result.paymentId shouldBe paymentId
                result.amount shouldBe BigDecimal("50000")
            }
        }
    }
})