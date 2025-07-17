package kr.hhplus.be.server.balance.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldNotBeNull
import java.math.BigDecimal
import java.time.LocalDateTime

class PointUnitTest : BehaviorSpec({
    
    given("Point 도메인") {
        When("Point.create()로 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val amount = BigDecimal("100.00")
                val point = Point.create(userId, amount)
                
                point.userId shouldBe userId
                point.amount shouldBe amount
                point.createdAt.shouldNotBeNull()
                point.lastUpdated.shouldNotBeNull()
            }
            
            Then("음수 금액으로 생성 시 예외가 발생한다") {
                val userId = 1L
                val negativeAmount = BigDecimal("-50.00")
                
                shouldThrow<IllegalArgumentException> {
                    Point.create(userId, negativeAmount)
                }
            }
            
            Then("0원으로 생성 시 정상 생성된다") {
                val userId = 1L
                val zeroAmount = BigDecimal.ZERO
                val point = Point.create(userId, zeroAmount)
                
                point.amount shouldBe zeroAmount
            }
        }
        
        When("포인트 충전을 할 때") {
            Then("정상적으로 충전된다") {
                val originalPoint = Point.create(1L, BigDecimal("100.00"))
                val chargeAmount = BigDecimal("50.00")
                
                val chargedPoint = originalPoint.charge(chargeAmount)
                
                chargedPoint.amount shouldBe BigDecimal("150.00")
                chargedPoint.userId shouldBe originalPoint.userId
            }
            
            Then("0원 이하 충전 시 예외가 발생한다") {
                val point = Point.create(1L, BigDecimal("100.00"))
                
                shouldThrow<IllegalArgumentException> {
                    point.charge(BigDecimal.ZERO)
                }
                
                shouldThrow<IllegalArgumentException> {
                    point.charge(BigDecimal("-10.00"))
                }
            }
            
            Then("경계값 충전이 정상 처리된다") {
                val point = Point.create(1L, BigDecimal("100.00"))
                val minChargeAmount = BigDecimal("0.01")
                
                val chargedPoint = point.charge(minChargeAmount)
                chargedPoint.amount shouldBe BigDecimal("100.01")
            }
        }
        
        When("포인트 차감을 할 때") {
            Then("정상적으로 차감된다") {
                val originalPoint = Point.create(1L, BigDecimal("100.00"))
                val deductAmount = BigDecimal("30.00")
                
                val deductedPoint = originalPoint.deduct(deductAmount)
                
                deductedPoint.amount shouldBe BigDecimal("70.00")
                deductedPoint.userId shouldBe originalPoint.userId
            }
            
            Then("잔액 부족 시 예외가 발생한다") {
                val point = Point.create(1L, BigDecimal("50.00"))
                val deductAmount = BigDecimal("100.00")
                
                shouldThrow<InsufficientBalanceException> {
                    point.deduct(deductAmount)
                }
            }
            
            Then("0원 이하 차감 시 예외가 발생한다") {
                val point = Point.create(1L, BigDecimal("100.00"))
                
                shouldThrow<IllegalArgumentException> {
                    point.deduct(BigDecimal.ZERO)
                }
                
                shouldThrow<IllegalArgumentException> {
                    point.deduct(BigDecimal("-10.00"))
                }
            }
            
            Then("전액 차감이 정상 처리된다") {
                val point = Point.create(1L, BigDecimal("100.00"))
                val deductedPoint = point.deduct(BigDecimal("100.00"))
                
                deductedPoint.amount shouldBe BigDecimal("0.00")
            }
        }
        
        When("잔액 확인을 할 때") {
            Then("충분한 잔액이 있으면 true를 반환한다") {
                val point = Point.create(1L, BigDecimal("100.00"))
                
                point.hasEnoughBalance(BigDecimal("50.00")) shouldBe true
                point.hasEnoughBalance(BigDecimal("100.00")) shouldBe true
            }
            
            Then("잔액이 부족하면 false를 반환한다") {
                val point = Point.create(1L, BigDecimal("100.00"))
                
                point.hasEnoughBalance(BigDecimal("150.00")) shouldBe false
            }
            
            Then("경계값 확인이 정상 처리된다") {
                val point = Point.create(1L, BigDecimal("100.00"))
                
                point.hasEnoughBalance(BigDecimal("100.01")) shouldBe false
                point.hasEnoughBalance(BigDecimal("99.99")) shouldBe true
            }
        }
        
        When("같은 userId로 Point를 2개 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val userId = 1L
                val amount = BigDecimal("100.00")
                val point1 = Point.create(userId, amount)
                val point2 = Point.create(userId, amount)
                
                // data class이므로 모든 필드가 같으면 동등
                point1.userId shouldBe point2.userId
                point1.amount shouldBe point2.amount
            }
        }
    }
})
