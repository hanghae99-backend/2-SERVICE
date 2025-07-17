package kr.hhplus.be.server.balance.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.balance.entity.Point
import java.math.BigDecimal

class BalanceResponseUnitTest : BehaviorSpec({
    
    given("BalanceResponse DTO") {
        When("Point 엔티티에서 변환할 때") {
            Then("정상적으로 변환된다") {
                val point = Point.create(1L, BigDecimal("100.00"))
                
                val response = BalanceResponse.from(point)
                
                response.userId shouldBe point.userId
                response.balance shouldBe point.amount
                response.lastUpdated shouldBe point.lastUpdated.toString()
            }
            
            Then("0원 잔액도 정상 변환된다") {
                val point = Point.create(1L, BigDecimal.ZERO)
                
                val response = BalanceResponse.from(point)
                
                response.userId shouldBe point.userId
                response.balance shouldBe BigDecimal.ZERO
                response.lastUpdated shouldBe point.lastUpdated.toString()
            }
            
            Then("큰 금액도 정상 변환된다") {
                val point = Point.create(Long.MAX_VALUE, BigDecimal("999999.99"))
                
                val response = BalanceResponse.from(point)
                
                response.userId shouldBe Long.MAX_VALUE
                response.balance shouldBe BigDecimal("999999.99")
                response.lastUpdated shouldBe point.lastUpdated.toString()
            }
        }
        
        When("직접 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val balance = BigDecimal("100.00")
                val lastUpdated = "2023-01-01T00:00:00"
                
                val response = BalanceResponse(userId, balance, lastUpdated)
                
                response.userId shouldBe userId
                response.balance shouldBe balance
                response.lastUpdated shouldBe lastUpdated
            }
        }
        
        When("같은 값으로 두 개의 response를 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val userId = 1L
                val balance = BigDecimal("100.00")
                val lastUpdated = "2023-01-01T00:00:00"
                
                val response1 = BalanceResponse(userId, balance, lastUpdated)
                val response2 = BalanceResponse(userId, balance, lastUpdated)
                
                response1 shouldBe response2
                response1.hashCode() shouldBe response2.hashCode()
            }
        }
    }
})
