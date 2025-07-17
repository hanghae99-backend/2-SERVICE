package kr.hhplus.be.server.balance.dto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class UseBalanceRequestUnitTest : BehaviorSpec({
    
    given("UseBalanceRequest DTO") {
        When("정상적인 값으로 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val amount = BigDecimal("100.00")
                
                val request = UseBalanceRequest(userId, amount)
                
                request.userId shouldBe userId
                request.amount shouldBe amount
            }
            
            Then("경계값으로 생성할 때 정상 처리된다") {
                val userId = 1L
                val minAmount = BigDecimal("0.01")
                
                val request = UseBalanceRequest(userId, minAmount)
                
                request.userId shouldBe userId
                request.amount shouldBe minAmount
            }
            
            Then("큰 금액으로 생성할 때 정상 처리된다") {
                val userId = Long.MAX_VALUE
                val largeAmount = BigDecimal("999999.99")
                
                val request = UseBalanceRequest(userId, largeAmount)
                
                request.userId shouldBe userId
                request.amount shouldBe largeAmount
            }
        }
        
        When("유효하지 않은 값으로 생성할 때") {
            Then("0원으로 생성 시 예외가 발생한다") {
                val userId = 1L
                val zeroAmount = BigDecimal.ZERO
                
                shouldThrow<IllegalArgumentException> {
                    UseBalanceRequest(userId, zeroAmount)
                }
            }
            
            Then("음수 금액으로 생성 시 예외가 발생한다") {
                val userId = 1L
                val negativeAmount = BigDecimal("-50.00")
                
                shouldThrow<IllegalArgumentException> {
                    UseBalanceRequest(userId, negativeAmount)
                }
            }
            
            Then("예외 메시지가 올바르다") {
                val userId = 1L
                val invalidAmount = BigDecimal("-10.00")
                
                val exception = shouldThrow<IllegalArgumentException> {
                    UseBalanceRequest(userId, invalidAmount)
                }
                
                exception.message shouldBe "사용 금액은 0보다 커야 합니다"
            }
        }
        
        When("같은 값으로 두 개의 request를 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val userId = 1L
                val amount = BigDecimal("100.00")
                
                val request1 = UseBalanceRequest(userId, amount)
                val request2 = UseBalanceRequest(userId, amount)
                
                request1 shouldBe request2
                request1.hashCode() shouldBe request2.hashCode()
            }
        }
    }
})
