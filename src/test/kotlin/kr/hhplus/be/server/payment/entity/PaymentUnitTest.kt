package kr.hhplus.be.server.payment.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import java.math.BigDecimal

class PaymentUnitTest : BehaviorSpec({
    
    given("Payment 도메인") {
        When("Payment.create()로 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val reservationId = 1L
                val amount = BigDecimal("100.00")
                
                val payment = Payment.create(userId, reservationId, amount)
                
                payment.userId shouldBe userId
                payment.reservationId shouldBe reservationId
                payment.amount shouldBe amount
                payment.status shouldBe PaymentStatus.PENDING
                payment.paidAt.shouldBeNull()
                payment.createdAt.shouldNotBeNull()
            }
            
            Then("0원 이하 금액으로 생성 시 예외가 발생한다") {
                val userId = 1L
                val reservationId = 1L
                val zeroAmount = BigDecimal.ZERO
                
                shouldThrow<IllegalArgumentException> {
                    Payment.create(userId, reservationId, zeroAmount)
                }
                
                val negativeAmount = BigDecimal("-50.00")
                shouldThrow<IllegalArgumentException> {
                    Payment.create(userId, reservationId, negativeAmount)
                }
            }
            
            Then("경계값으로 생성 시 정상 처리된다") {
                val userId = 1L
                val reservationId = 1L
                val minAmount = BigDecimal("0.01")
                
                val payment = Payment.create(userId, reservationId, minAmount)
                payment.amount shouldBe minAmount
                payment.status shouldBe PaymentStatus.PENDING
            }
        }
        
        When("결제 완료 처리할 때") {
            Then("PENDING 상태에서 COMPLETED로 변경된다") {
                val payment = Payment.create(1L, 1L, BigDecimal("100.00"))
                
                val completedPayment = payment.complete()
                
                completedPayment.status shouldBe PaymentStatus.COMPLETED
                completedPayment.paidAt.shouldNotBeNull()
                completedPayment.userId shouldBe payment.userId
                completedPayment.reservationId shouldBe payment.reservationId
                completedPayment.amount shouldBe payment.amount
            }
            
            Then("PENDING 상태가 아닌 경우 예외가 발생한다") {
                val payment = Payment.create(1L, 1L, BigDecimal("100.00"))
                val completedPayment = payment.complete()
                
                shouldThrow<PaymentAlreadyProcessedException> {
                    completedPayment.complete()
                }
            }
        }
        
        When("결제 실패 처리할 때") {
            Then("PENDING 상태에서 FAILED로 변경된다") {
                val payment = Payment.create(1L, 1L, BigDecimal("100.00"))
                
                val failedPayment = payment.fail()
                
                failedPayment.status shouldBe PaymentStatus.FAILED
                failedPayment.paidAt.shouldBeNull()
                failedPayment.userId shouldBe payment.userId
                failedPayment.reservationId shouldBe payment.reservationId
                failedPayment.amount shouldBe payment.amount
            }
            
            Then("PENDING 상태가 아닌 경우 예외가 발생한다") {
                val payment = Payment.create(1L, 1L, BigDecimal("100.00"))
                val failedPayment = payment.fail()
                
                shouldThrow<PaymentAlreadyProcessedException> {
                    failedPayment.fail()
                }
            }
        }
        
        When("결제 상태를 확인할 때") {
            Then("isCompleted()가 정상 동작한다") {
                val pendingPayment = Payment.create(1L, 1L, BigDecimal("100.00"))
                val completedPayment = pendingPayment.complete()
                val failedPayment = pendingPayment.fail()
                
                pendingPayment.isCompleted() shouldBe false
                completedPayment.isCompleted() shouldBe true
                failedPayment.isCompleted() shouldBe false
            }
            
            Then("isPending()이 정상 동작한다") {
                val pendingPayment = Payment.create(1L, 1L, BigDecimal("100.00"))
                val completedPayment = pendingPayment.complete()
                val failedPayment = pendingPayment.fail()
                
                pendingPayment.isPending() shouldBe true
                completedPayment.isPending() shouldBe false
                failedPayment.isPending() shouldBe false
            }
        }
        
        When("같은 값으로 Payment를 2개 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val userId = 1L
                val reservationId = 1L
                val amount = BigDecimal("100.00")
                
                val payment1 = Payment.create(userId, reservationId, amount)
                val payment2 = Payment.create(userId, reservationId, amount)
                
                // data class이므로 모든 필드가 같으면 동등 (createdAt은 다를 수 있음)
                payment1.userId shouldBe payment2.userId
                payment1.reservationId shouldBe payment2.reservationId
                payment1.amount shouldBe payment2.amount
                payment1.status shouldBe payment2.status
            }
        }
    }
    
    given("PaymentStatus enum") {
        When("enum 값들을 확인할 때") {
            Then("PENDING, COMPLETED, FAILED 상태가 존재한다") {
                PaymentStatus.PENDING.name shouldBe "PENDING"
                PaymentStatus.COMPLETED.name shouldBe "COMPLETED"
                PaymentStatus.FAILED.name shouldBe "FAILED"
            }
        }
    }
})
