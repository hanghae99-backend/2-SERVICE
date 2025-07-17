package kr.hhplus.be.server.payment.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.payment.entity.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentResponseUnitTest : BehaviorSpec({
    
    given("PaymentResponse DTO") {
        When("Payment 엔티티에서 변환할 때") {
            Then("PENDING 상태가 정상 변환된다") {
                val payment = Payment.create(1L, 1L, BigDecimal("100.00"))
                
                val response = PaymentResponse.from(payment)
                
                response.paymentId shouldBe payment.paymentId
                response.userId shouldBe payment.userId
                response.reservationId shouldBe payment.reservationId
                response.amount shouldBe payment.amount
                response.status shouldBe "PENDING"
                response.paidAt shouldBe null
                response.createdAt shouldBe payment.createdAt.toString()
            }
            
            Then("COMPLETED 상태가 정상 변환된다") {
                val payment = Payment.create(1L, 1L, BigDecimal("100.00"))
                val completedPayment = payment.complete()
                
                val response = PaymentResponse.from(completedPayment)
                
                response.paymentId shouldBe completedPayment.paymentId
                response.userId shouldBe completedPayment.userId
                response.reservationId shouldBe completedPayment.reservationId
                response.amount shouldBe completedPayment.amount
                response.status shouldBe "COMPLETED"
                response.paidAt shouldBe completedPayment.paidAt?.toString()
                response.createdAt shouldBe completedPayment.createdAt.toString()
            }
            
            Then("FAILED 상태가 정상 변환된다") {
                val payment = Payment.create(1L, 1L, BigDecimal("100.00"))
                val failedPayment = payment.fail()
                
                val response = PaymentResponse.from(failedPayment)
                
                response.paymentId shouldBe failedPayment.paymentId
                response.userId shouldBe failedPayment.userId
                response.reservationId shouldBe failedPayment.reservationId
                response.amount shouldBe failedPayment.amount
                response.status shouldBe "FAILED"
                response.paidAt shouldBe null
                response.createdAt shouldBe failedPayment.createdAt.toString()
            }
            
            Then("경계값이 정상 변환된다") {
                val payment = Payment.create(Long.MAX_VALUE, Long.MAX_VALUE, BigDecimal("0.01"))
                
                val response = PaymentResponse.from(payment)
                
                response.userId shouldBe Long.MAX_VALUE
                response.reservationId shouldBe Long.MAX_VALUE
                response.amount shouldBe BigDecimal("0.01")
                response.status shouldBe "PENDING"
            }
        }
        
        When("직접 생성할 때") {
            Then("정상적으로 생성된다") {
                val paymentId = 1L
                val userId = 1L
                val reservationId = 1L
                val amount = BigDecimal("100.00")
                val status = "COMPLETED"
                val paidAt = "2023-01-01T00:00:00"
                val createdAt = "2023-01-01T00:00:00"
                
                val response = PaymentResponse(
                    paymentId = paymentId,
                    userId = userId,
                    reservationId = reservationId,
                    amount = amount,
                    status = status,
                    paidAt = paidAt,
                    createdAt = createdAt
                )
                
                response.paymentId shouldBe paymentId
                response.userId shouldBe userId
                response.reservationId shouldBe reservationId
                response.amount shouldBe amount
                response.status shouldBe status
                response.paidAt shouldBe paidAt
                response.createdAt shouldBe createdAt
            }
        }
        
        When("같은 값으로 두 개의 response를 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val paymentId = 1L
                val userId = 1L
                val reservationId = 1L
                val amount = BigDecimal("100.00")
                val status = "COMPLETED"
                val paidAt = "2023-01-01T00:00:00"
                val createdAt = "2023-01-01T00:00:00"
                
                val response1 = PaymentResponse(paymentId, userId, reservationId, amount, status, paidAt, createdAt)
                val response2 = PaymentResponse(paymentId, userId, reservationId, amount, status, paidAt, createdAt)
                
                response1 shouldBe response2
                response1.hashCode() shouldBe response2.hashCode()
            }
        }
    }
})
