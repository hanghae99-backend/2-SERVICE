package kr.hhplus.be.server.domain.payment.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.repositories.PaymentRepository
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.domain.payment.kafka.PaymentEventProducer
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.global.client.BalanceApiClient
import kr.hhplus.be.server.global.client.ReservationApiClient
import kr.hhplus.be.server.domain.auth.service.ActiveTokenService
import java.math.BigDecimal

class PaymentServiceTest : DescribeSpec({
    
    val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    val paymentStatusTypeRepository = mockk<PaymentStatusTypePojoRepository>(relaxed = true)
    val paymentEventProducer = mockk<PaymentEventProducer>(relaxed = true)
    val balanceApiClient = mockk<BalanceApiClient>(relaxed = true)
    val reservationApiClient = mockk<ReservationApiClient>(relaxed = true)
    val activeTokenService = mockk<ActiveTokenService>(relaxed = true)
    
    val paymentService = PaymentService(
        paymentRepository,
        paymentStatusTypeRepository,
        paymentEventProducer,
        balanceApiClient,
        reservationApiClient,
        activeTokenService
    )
    
    describe("createReservationPayment") {
        context("유효한 예약에 대해 결제를 생성할 때") {
            it("결제가 성공적으로 생성되어야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val amount = BigDecimal("50000")
                
                val pendingStatus = PaymentStatusType.createDefault("PENDING", "대기", PaymentStatusType.CATEGORY_NORMAL)
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
    
    describe("getPaymentById") {
        context("존재하는 결제 ID로 조회할 때") {
            it("결제 정보를 반환해야 한다") {
                // given
                val paymentId = 1L
                val pendingStatus = PaymentStatusType.createDefault("PENDING", "대기", PaymentStatusType.CATEGORY_NORMAL)
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
        
        context("존재하지 않는 결제 ID로 조회할 때") {
            it("PaymentNotFoundException이 발생해야 한다") {
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
