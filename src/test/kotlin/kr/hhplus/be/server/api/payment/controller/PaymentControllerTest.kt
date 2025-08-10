package kr.hhplus.be.server.api.payment.controller

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.api.payment.usecase.ProcessPaymentUserCase
import kr.hhplus.be.server.domain.payment.service.PaymentService
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentControllerTest : DescribeSpec({
    
    val processPaymentUserCase = mockk<ProcessPaymentUserCase>()
    val paymentService = mockk<PaymentService>()
    val paymentController = PaymentController(processPaymentUserCase, paymentService)
    
    describe("processPayment") {
        context("유효한 결제 요청이 들어올 때") {
            it("결제를 처리하고 응답을 반환해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val request = PaymentRequest(userId, reservationId, token)
                val paymentDto = PaymentDto(
                    paymentId = 1L,
                    userId = userId,
                    amount = BigDecimal("50000"),
                    paymentMethod = "POINT",
                    statusCode = "COMPLETED",
                    paidAt = LocalDateTime.now(),
                )
                
                every { processPaymentUserCase.execute(userId, reservationId, token) } returns paymentDto
                
                // when
                val response = paymentController.processPayment(request)
                
                // then
                response shouldNotBe null
                response.statusCode.value() shouldBe 201
                response.body?.success shouldBe true
                response.body?.data?.paymentId shouldBe 1L
                response.body?.message shouldBe "결제가 완료되었습니다"
            }
        }
    }

    describe("getPayment") {
        context("존재하는 결제 ID로 조회할 때") {
            it("결제 정보를 반환해야 한다") {
                // given
                val paymentId = 1L
                val paymentDto = PaymentDto(
                    paymentId = paymentId,
                    userId = 1L,
                    amount = BigDecimal("50000"),
                    paymentMethod = "POINT",
                    statusCode = "COMPLETED",
                    paidAt = LocalDateTime.now(),
                )
                
                every { paymentService.getPaymentById(paymentId) } returns paymentDto
                
                // when
                val response = paymentController.getPayment(paymentId)
                
                // then
                response shouldNotBe null
                response.statusCode.value() shouldBe 200
                response.body?.success shouldBe true
                response.body?.data?.paymentId shouldBe paymentId
                response.body?.message shouldBe "결제 정보 조회가 완료되었습니다"
            }
        }
    }
})
