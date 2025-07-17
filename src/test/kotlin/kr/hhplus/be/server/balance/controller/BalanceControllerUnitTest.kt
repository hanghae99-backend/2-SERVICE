package kr.hhplus.be.server.balance.controller

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kr.hhplus.be.server.balance.dto.ChargeBalanceRequest
import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.balance.service.BalanceService
import org.springframework.http.HttpStatus
import java.math.BigDecimal

class BalanceControllerUnitTest : BehaviorSpec({
    val balanceService = mockk<BalanceService>(relaxed = true)
    val balanceController = BalanceController(balanceService)

    beforeTest {
        clearMocks(balanceService, answers = false, recordedCalls = true)
    }

    given("BalanceController는 잔액 관리 HTTP 인터페이스의 책임을 가진다") {
        When("포인트 충전 요청을 받으면") {
            Then("서비스에 위임하여 충전을 처리한다") {
                val userId = 1L
                val chargeAmount = BigDecimal("100.00")
                val chargedPoint = Point.create(userId, BigDecimal("100.00"))
                
                every { balanceService.chargeBalance(userId, chargeAmount) } returns chargedPoint

                val response = balanceController.charge(ChargeBalanceRequest(userId, chargeAmount))

                // then - BalanceController의 책임: HTTP 인터페이스 제공 + 서비스 위임
                response.statusCode shouldBe HttpStatus.OK
                response.body?.userId shouldBe userId
                response.body?.balance shouldBe BigDecimal("100.00")
                response.body?.lastUpdated shouldBe chargedPoint.lastUpdated.toString()
                
                verify(exactly = 1) { balanceService.chargeBalance(userId, chargeAmount) }
            }
            
            Then("JSON 요청으로 충전을 처리한다") {
                val request = ChargeBalanceRequest(1L, BigDecimal("100.00"))
                val chargedPoint = Point.create(request.userId, BigDecimal("100.00"))
                
                every { balanceService.chargeBalance(request.userId, request.amount) } returns chargedPoint

                val response = balanceController.charge(request)

                response.statusCode shouldBe HttpStatus.OK
                response.body?.userId shouldBe request.userId
                response.body?.balance shouldBe BigDecimal("100.00")
                
                verify(exactly = 1) { balanceService.chargeBalance(request.userId, request.amount) }
            }
            
            Then("경계값 충전 요청도 정상 처리한다") {
                val userId = 1L
                val minAmount = BigDecimal("0.01")
                val chargedPoint = Point.create(userId, minAmount)
                
                every { balanceService.chargeBalance(userId, minAmount) } returns chargedPoint

                val response = balanceController.charge(ChargeBalanceRequest(userId, minAmount))

                response.statusCode shouldBe HttpStatus.OK
                response.body?.balance shouldBe BigDecimal("0.01")
                verify(exactly = 1) { balanceService.chargeBalance(userId, minAmount) }
            }
            
            Then("큰 금액 충전 요청도 정상 처리한다") {
                val userId = 1L
                val largeAmount = BigDecimal("999999.99")
                val chargedPoint = Point.create(userId, largeAmount)
                
                every { balanceService.chargeBalance(userId, largeAmount) } returns chargedPoint

                val response = balanceController.charge(ChargeBalanceRequest(userId, largeAmount))

                response.statusCode shouldBe HttpStatus.OK
                response.body?.balance shouldBe BigDecimal("999999.99")
                verify(exactly = 1) { balanceService.chargeBalance(userId, largeAmount) }
            }
        }
        
        When("잔액 조회 요청을 받으면") {
            Then("서비스에 위임하여 잔액을 조회한다") {
                val userId = 1L
                val existingPoint = Point.create(userId, BigDecimal("150.00"))
                
                every { balanceService.getBalance(userId) } returns existingPoint

                val response = balanceController.getBalance(userId)

                // then - BalanceController의 책임: HTTP 인터페이스 제공 + 서비스 위임
                response.statusCode shouldBe HttpStatus.OK
                response.body?.userId shouldBe userId
                response.body?.balance shouldBe BigDecimal("150.00")
                response.body?.lastUpdated shouldBe existingPoint.lastUpdated.toString()
                
                verify(exactly = 1) { balanceService.getBalance(userId) }
            }
            
            Then("0원 잔액도 정상 조회한다") {
                val userId = 1L
                val zeroPoint = Point.create(userId, BigDecimal.ZERO)
                
                every { balanceService.getBalance(userId) } returns zeroPoint

                val response = balanceController.getBalance(userId)

                response.statusCode shouldBe HttpStatus.OK
                response.body?.balance shouldBe BigDecimal.ZERO
                verify(exactly = 1) { balanceService.getBalance(userId) }
            }
            
            Then("경계값 사용자 ID도 정상 처리한다") {
                val userId = Long.MAX_VALUE
                val existingPoint = Point.create(userId, BigDecimal("100.00"))
                
                every { balanceService.getBalance(userId) } returns existingPoint

                val response = balanceController.getBalance(userId)

                response.statusCode shouldBe HttpStatus.OK
                response.body?.userId shouldBe Long.MAX_VALUE
                verify(exactly = 1) { balanceService.getBalance(userId) }
            }
        }
        
        When("포인트 이력 조회 요청을 받으면") {
            Then("서비스에 위임하여 이력을 조회한다") {
                val userId = 1L
                val histories = listOf(
                    PointHistory.charge(userId, BigDecimal("100.00"), "충전"),
                    PointHistory.usage(userId, BigDecimal("50.00"), "사용")
                )
                
                every { balanceService.getPointHistory(userId) } returns histories

                val response = balanceController.history(userId)

                // then - BalanceController의 책임: HTTP 인터페이스 제공 + 서비스 위임
                response.statusCode shouldBe HttpStatus.OK
                response.body?.size shouldBe 2
                
                val firstHistory = response.body?.get(0)
                firstHistory?.userId shouldBe userId
                firstHistory?.amount shouldBe BigDecimal("100.00")
                firstHistory?.type shouldBe "CHARGE"
                firstHistory?.description shouldBe "충전"
                
                val secondHistory = response.body?.get(1)
                secondHistory?.userId shouldBe userId
                secondHistory?.amount shouldBe BigDecimal("50.00")
                secondHistory?.type shouldBe "USAGE"
                secondHistory?.description shouldBe "사용"
                
                verify(exactly = 1) { balanceService.getPointHistory(userId) }
            }
            
            Then("빈 이력도 정상 조회한다") {
                val userId = 1L
                val emptyHistories = emptyList<PointHistory>()
                
                every { balanceService.getPointHistory(userId) } returns emptyHistories

                val response = balanceController.history(userId)

                response.statusCode shouldBe HttpStatus.OK
                response.body?.size shouldBe 0
                verify(exactly = 1) { balanceService.getPointHistory(userId) }
            }
            
            Then("많은 이력도 정상 조회한다") {
                val userId = 1L
                val manyHistories = (1..100).map { 
                    PointHistory.charge(userId, BigDecimal("10.00"), "충전 $it")
                }
                
                every { balanceService.getPointHistory(userId) } returns manyHistories

                val response = balanceController.history(userId)

                response.statusCode shouldBe HttpStatus.OK
                response.body?.size shouldBe 100
                verify(exactly = 1) { balanceService.getPointHistory(userId) }
            }
        }
    }
    
    given("BalanceController는 DTO 검증 책임을 가진다") {
        When("유효하지 않은 충전 요청을 받으면") {
            Then("DTO 검증에서 예외가 발생한다") {
                val userId = 1L
                val invalidAmount = BigDecimal("-50.00")
                
                val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    ChargeBalanceRequest(userId, invalidAmount)
                }
                
                exception.message shouldBe "충전 금액은 0보다 커야 합니다"
            }
        }
    }
})
