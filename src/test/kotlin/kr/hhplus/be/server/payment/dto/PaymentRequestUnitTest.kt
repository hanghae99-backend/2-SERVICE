package kr.hhplus.be.server.payment.dto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PaymentRequestUnitTest : BehaviorSpec({
    
    given("PaymentRequest DTO") {
        When("정상적인 값으로 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                
                val request = PaymentRequest(userId, reservationId, token)
                
                request.userId shouldBe userId
                request.reservationId shouldBe reservationId
                request.token shouldBe token
            }
            
            Then("경계값으로 생성할 때 정상 처리된다") {
                val userId = 1L
                val reservationId = 1L
                val token = "a"
                
                val request = PaymentRequest(userId, reservationId, token)
                
                request.userId shouldBe userId
                request.reservationId shouldBe reservationId
                request.token shouldBe token
            }
            
            Then("큰 값으로 생성할 때 정상 처리된다") {
                val userId = Long.MAX_VALUE
                val reservationId = Long.MAX_VALUE
                val token = "very-long-token-string-for-testing"
                
                val request = PaymentRequest(userId, reservationId, token)
                
                request.userId shouldBe userId
                request.reservationId shouldBe reservationId
                request.token shouldBe token
            }
        }
        
        When("유효하지 않은 값으로 생성할 때") {
            Then("userId가 0 이하일 때 예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    PaymentRequest(0L, 1L, "token")
                }
                
                shouldThrow<IllegalArgumentException> {
                    PaymentRequest(-1L, 1L, "token")
                }
            }
            
            Then("reservationId가 0 이하일 때 예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    PaymentRequest(1L, 0L, "token")
                }
                
                shouldThrow<IllegalArgumentException> {
                    PaymentRequest(1L, -1L, "token")
                }
            }
            
            Then("token이 비어있을 때 예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    PaymentRequest(1L, 1L, "")
                }
                
                shouldThrow<IllegalArgumentException> {
                    PaymentRequest(1L, 1L, "   ")
                }
            }
            
            Then("예외 메시지가 올바르다") {
                val userIdException = shouldThrow<IllegalArgumentException> {
                    PaymentRequest(0L, 1L, "token")
                }
                userIdException.message shouldBe "사용자 ID는 0보다 커야 합니다"
                
                val reservationIdException = shouldThrow<IllegalArgumentException> {
                    PaymentRequest(1L, 0L, "token")
                }
                reservationIdException.message shouldBe "예약 ID는 0보다 커야 합니다"
                
                val tokenException = shouldThrow<IllegalArgumentException> {
                    PaymentRequest(1L, 1L, "")
                }
                tokenException.message shouldBe "토큰은 비어있을 수 없습니다"
            }
        }
        
        When("같은 값으로 두 개의 request를 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val userId = 1L
                val reservationId = 1L
                val token = "token"
                
                val request1 = PaymentRequest(userId, reservationId, token)
                val request2 = PaymentRequest(userId, reservationId, token)
                
                request1 shouldBe request2
                request1.hashCode() shouldBe request2.hashCode()
            }
        }
    }
})
