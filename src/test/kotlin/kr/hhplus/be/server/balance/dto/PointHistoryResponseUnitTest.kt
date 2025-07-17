package kr.hhplus.be.server.balance.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.balance.entity.PointHistoryType
import java.math.BigDecimal

class PointHistoryResponseUnitTest : BehaviorSpec({
    
    given("PointHistoryResponse DTO") {
        When("PointHistory 엔티티에서 변환할 때") {
            Then("CHARGE 타입이 정상 변환된다") {
                val history = PointHistory.charge(1L, BigDecimal("100.00"), "충전 테스트")
                
                val response = PointHistoryResponse.from(history)
                
                response.historyId shouldBe history.historyId
                response.userId shouldBe history.userId
                response.amount shouldBe history.amount
                response.type shouldBe "CHARGE"
                response.description shouldBe history.description
                response.createdAt shouldBe history.createdAt.toString()
            }
            
            Then("USAGE 타입이 정상 변환된다") {
                val history = PointHistory.usage(1L, BigDecimal("50.00"), "사용 테스트")
                
                val response = PointHistoryResponse.from(history)
                
                response.historyId shouldBe history.historyId
                response.userId shouldBe history.userId
                response.amount shouldBe history.amount
                response.type shouldBe "USAGE"
                response.description shouldBe history.description
                response.createdAt shouldBe history.createdAt.toString()
            }
            
            Then("경계값이 정상 변환된다") {
                val history = PointHistory.charge(Long.MAX_VALUE, BigDecimal("0.01"), "최소 충전")
                
                val response = PointHistoryResponse.from(history)
                
                response.userId shouldBe Long.MAX_VALUE
                response.amount shouldBe BigDecimal("0.01")
                response.type shouldBe "CHARGE"
                response.description shouldBe "최소 충전"
            }
        }
        
        When("직접 생성할 때") {
            Then("정상적으로 생성된다") {
                val historyId = 1L
                val userId = 1L
                val amount = BigDecimal("100.00")
                val type = "CHARGE"
                val description = "직접 생성"
                val createdAt = "2023-01-01T00:00:00"
                
                val response = PointHistoryResponse(
                    historyId = historyId,
                    userId = userId,
                    amount = amount,
                    type = type,
                    description = description,
                    createdAt = createdAt
                )
                
                response.historyId shouldBe historyId
                response.userId shouldBe userId
                response.amount shouldBe amount
                response.type shouldBe type
                response.description shouldBe description
                response.createdAt shouldBe createdAt
            }
        }
        
        When("같은 값으로 두 개의 response를 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val historyId = 1L
                val userId = 1L
                val amount = BigDecimal("100.00")
                val type = "CHARGE"
                val description = "테스트"
                val createdAt = "2023-01-01T00:00:00"
                
                val response1 = PointHistoryResponse(historyId, userId, amount, type, description, createdAt)
                val response2 = PointHistoryResponse(historyId, userId, amount, type, description, createdAt)
                
                response1 shouldBe response2
                response1.hashCode() shouldBe response2.hashCode()
            }
        }
    }
})
