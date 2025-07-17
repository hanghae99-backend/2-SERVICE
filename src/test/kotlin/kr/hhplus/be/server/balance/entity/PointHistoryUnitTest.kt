package kr.hhplus.be.server.balance.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import java.math.BigDecimal

class PointHistoryUnitTest : BehaviorSpec({
    
    given("PointHistory 도메인") {
        When("PointHistory.charge()로 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val amount = BigDecimal("100.00")
                val description = "테스트 충전"
                
                val history = PointHistory.charge(userId, amount, description)
                
                history.userId shouldBe userId
                history.amount shouldBe amount
                history.type shouldBe PointHistoryType.CHARGE
                history.description shouldBe description
                history.createdAt.shouldNotBeNull()
            }
            
            Then("기본 설명으로 생성된다") {
                val userId = 1L
                val amount = BigDecimal("100.00")
                
                val history = PointHistory.charge(userId, amount)
                
                history.description shouldBe "포인트 충전"
            }
            
            Then("0원 이하 충전 시 예외가 발생한다") {
                val userId = 1L
                
                shouldThrow<IllegalArgumentException> {
                    PointHistory.charge(userId, BigDecimal.ZERO)
                }
                
                shouldThrow<IllegalArgumentException> {
                    PointHistory.charge(userId, BigDecimal("-10.00"))
                }
            }
            
            Then("경계값 충전이 정상 처리된다") {
                val userId = 1L
                val minAmount = BigDecimal("0.01")
                
                val history = PointHistory.charge(userId, minAmount)
                history.amount shouldBe minAmount
                history.type shouldBe PointHistoryType.CHARGE
            }
        }
        
        When("PointHistory.usage()로 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val amount = BigDecimal("50.00")
                val description = "테스트 사용"
                
                val history = PointHistory.usage(userId, amount, description)
                
                history.userId shouldBe userId
                history.amount shouldBe amount
                history.type shouldBe PointHistoryType.USAGE
                history.description shouldBe description
                history.createdAt.shouldNotBeNull()
            }
            
            Then("기본 설명으로 생성된다") {
                val userId = 1L
                val amount = BigDecimal("50.00")
                
                val history = PointHistory.usage(userId, amount)
                
                history.description shouldBe "포인트 사용"
            }
            
            Then("0원 이하 사용 시 예외가 발생한다") {
                val userId = 1L
                
                shouldThrow<IllegalArgumentException> {
                    PointHistory.usage(userId, BigDecimal.ZERO)
                }
                
                shouldThrow<IllegalArgumentException> {
                    PointHistory.usage(userId, BigDecimal("-10.00"))
                }
            }
            
            Then("경계값 사용이 정상 처리된다") {
                val userId = 1L
                val minAmount = BigDecimal("0.01")
                
                val history = PointHistory.usage(userId, minAmount)
                history.amount shouldBe minAmount
                history.type shouldBe PointHistoryType.USAGE
            }
        }
        
        When("생성자로 직접 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val amount = BigDecimal("100.00")
                val type = PointHistoryType.CHARGE
                val description = "직접 생성"
                
                val history = PointHistory(
                    userId = userId,
                    amount = amount,
                    type = type,
                    description = description
                )
                
                history.userId shouldBe userId
                history.amount shouldBe amount
                history.type shouldBe type
                history.description shouldBe description
                history.historyId shouldBe 0 // 기본값
                history.createdAt.shouldNotBeNull()
            }
        }
        
        When("같은 내용으로 PointHistory를 2개 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val userId = 1L
                val amount = BigDecimal("100.00")
                val description = "테스트"
                
                val history1 = PointHistory.charge(userId, amount, description)
                val history2 = PointHistory.charge(userId, amount, description)
                
                // data class이므로 모든 필드가 같으면 동등 (createdAt은 다를 수 있음)
                history1.userId shouldBe history2.userId
                history1.amount shouldBe history2.amount
                history1.type shouldBe history2.type
                history1.description shouldBe history2.description
            }
        }
        
        When("PointHistoryType enum을 확인할 때") {
            Then("CHARGE와 USAGE 타입이 존재한다") {
                PointHistoryType.CHARGE.name shouldBe "CHARGE"
                PointHistoryType.USAGE.name shouldBe "USAGE"
            }
        }
    }
})
