package kr.hhplus.be.server.balance.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.*
import kr.hhplus.be.server.auth.service.DomainValidator
import kr.hhplus.be.server.auth.service.ParameterValidator
import kr.hhplus.be.server.balance.entity.*
import kr.hhplus.be.server.balance.repository.PointHistoryRepository
import kr.hhplus.be.server.balance.repository.PointRepository
import kr.hhplus.be.server.user.entity.UserNotFoundException
import kr.hhplus.be.server.user.service.UserService
import java.math.BigDecimal

class BalanceServiceUnitTest : BehaviorSpec({
    val pointRepository = mockk<PointRepository>(relaxed = true)
    val pointHistoryRepository = mockk<PointHistoryRepository>(relaxed = true)
    val userService = mockk<UserService>(relaxed = true)
    val parameterValidator = mockk<BalanceParameterValidator>(relaxed = true)
    val domainValidator = mockk<BalanceDomainValidator>(relaxed = true)
    val balanceService = BalanceService(pointRepository, pointHistoryRepository, userService, parameterValidator, domainValidator )

    beforeTest {
        clearMocks(pointRepository, pointHistoryRepository, userService, answers = false, recordedCalls = true)
    }

    given("BalanceService는 사용자 잔액 관리의 책임을 가진다") {
        When("비즈니스 규칙에 따라 포인트 충전을 요청받으면") {
            Then("사용자 검증 후 포인트를 충전하고 이력을 저장한다") {
                val userId = 1L
                val chargeAmount = BigDecimal("100.00")
                val existingPoint = Point.create(userId, BigDecimal("50.00"))
                val chargedPoint = existingPoint.charge(chargeAmount)
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns existingPoint
                every { pointRepository.save(any<Point>()) } returns chargedPoint
                every { pointHistoryRepository.save(any<PointHistory>()) } returns mockk()

                val result = balanceService.chargeBalance(userId, chargeAmount)

                // then - BalanceService의 책임: 사용자 검증 + 포인트 충전 + 이력 저장
                result.amount shouldBe BigDecimal("150.00")
                result.userId shouldBe userId
                
                // 비즈니스 규칙: 사용자 검증 → 포인트 충전 → 이력 저장
                verify(exactly = 1) { userService.existsById(userId) }
                verify(exactly = 1) { pointRepository.findByUserId(userId) }
                verify(exactly = 1) { pointRepository.save(any<Point>()) }
                verify(exactly = 1) { pointHistoryRepository.save(any<PointHistory>()) }
            }
            
            Then("신규 사용자의 경우 0원에서 시작하여 충전한다") {
                val userId = 1L
                val chargeAmount = BigDecimal("100.00")
                val newPoint = Point.create(userId, BigDecimal.ZERO)
                val chargedPoint = newPoint.charge(chargeAmount)
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns null
                every { pointRepository.save(any<Point>()) } returns chargedPoint
                every { pointHistoryRepository.save(any<PointHistory>()) } returns mockk()

                val result = balanceService.chargeBalance(userId, chargeAmount)

                result.amount shouldBe BigDecimal("100.00")
                verify(exactly = 1) { pointRepository.save(any<Point>()) }
                verify(exactly = 1) { pointHistoryRepository.save(any<PointHistory>()) }
            }
            
            Then("존재하지 않는 사용자의 경우 예외를 발생시킨다") {
                val userId = 1L
                val chargeAmount = BigDecimal("100.00")
                
                every { userService.existsById(userId) } returns false

                val exception = shouldThrow<UserNotFoundException> {
                    balanceService.chargeBalance(userId, chargeAmount)
                }
                
                exception.message?.contains("존재하지 않는 사용자입니다") shouldBe true
                verify(exactly = 1) { userService.existsById(userId) }
                verify(exactly = 0) { pointRepository.save(any<Point>()) }
            }
            
            Then("유효하지 않은 충전 금액의 경우 예외를 발생시킨다") {
                val userId = 1L
                val invalidAmount = BigDecimal("-50.00")
                
                val exception = shouldThrow<InvalidPointAmountException> {
                    balanceService.chargeBalance(userId, invalidAmount)
                }
                
                exception.message?.contains("충전 금액은 0보다 커야 합니다") shouldBe true
                verify(exactly = 0) { userService.existsById(any()) }
            }
            
            Then("경계값 충전이 정상 처리된다") {
                val userId = 1L
                val minAmount = BigDecimal("0.01")
                val existingPoint = Point.create(userId, BigDecimal.ZERO)
                val chargedPoint = existingPoint.charge(minAmount)
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns existingPoint
                every { pointRepository.save(any<Point>()) } returns chargedPoint
                every { pointHistoryRepository.save(any<PointHistory>()) } returns mockk()

                val result = balanceService.chargeBalance(userId, minAmount)
                result.amount shouldBe BigDecimal("0.01")
            }
        }
        
        When("사용자 잔액 조회를 요청받으면") {
            Then("사용자 검증 후 잔액을 조회한다") {
                val userId = 1L
                val existingPoint = Point.create(userId, BigDecimal("100.00"))
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns existingPoint

                val result = balanceService.getBalance(userId)

                // then - BalanceService의 책임: 사용자 검증 + 잔액 조회
                result.amount shouldBe BigDecimal("100.00")
                result.userId shouldBe userId
                
                verify(exactly = 1) { userService.existsById(userId) }
                verify(exactly = 1) { pointRepository.findByUserId(userId) }
            }
            
            Then("포인트 정보가 없는 경우 0원으로 반환한다") {
                val userId = 1L
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns null

                val result = balanceService.getBalance(userId)

                result.amount shouldBe BigDecimal.ZERO
                result.userId shouldBe userId
            }
            
            Then("존재하지 않는 사용자의 경우 예외를 발생시킨다") {
                val userId = 1L
                
                every { userService.existsById(userId) } returns false

                val exception = shouldThrow<UserNotFoundException> {
                    balanceService.getBalance(userId)
                }
                
                exception.message?.contains("존재하지 않는 사용자입니다") shouldBe true
            }
        }
        
        When("포인트 차감을 요청받으면") {
            Then("사용자 검증 후 포인트를 차감하고 이력을 저장한다") {
                val userId = 1L
                val deductAmount = BigDecimal("50.00")
                val existingPoint = Point.create(userId, BigDecimal("100.00"))
                val deductedPoint = existingPoint.deduct(deductAmount)
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns existingPoint
                every { pointRepository.save(any<Point>()) } returns deductedPoint
                every { pointHistoryRepository.save(any<PointHistory>()) } returns mockk()

                val result = balanceService.deductBalance(userId, deductAmount)

                // then - BalanceService의 책임: 사용자 검증 + 포인트 차감 + 이력 저장
                result.amount shouldBe BigDecimal("50.00")
                result.userId shouldBe userId
                
                verify(exactly = 1) { userService.existsById(userId) }
                verify(exactly = 1) { pointRepository.findByUserId(userId) }
                verify(exactly = 1) { pointRepository.save(any<Point>()) }
                verify(exactly = 1) { pointHistoryRepository.save(any<PointHistory>()) }
            }
            
            Then("포인트 정보가 없는 경우 예외를 발생시킨다") {
                val userId = 1L
                val deductAmount = BigDecimal("50.00")
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns null

                val exception = shouldThrow<PointNotFoundException> {
                    balanceService.deductBalance(userId, deductAmount)
                }
                
                exception.message?.contains("포인트 정보를 찾을 수 없습니다") shouldBe true
            }
        }
        
        When("잔액 확인을 요청받으면") {
            Then("충분한 잔액이 있는지 확인한다") {
                val userId = 1L
                val checkAmount = BigDecimal("50.00")
                val existingPoint = Point.create(userId, BigDecimal("100.00"))
                
                every { pointRepository.findByUserId(userId) } returns existingPoint

                val result = balanceService.checkBalance(userId, checkAmount)

                result shouldBe true
                verify(exactly = 1) { pointRepository.findByUserId(userId) }
            }
            
            Then("포인트 정보가 없는 경우 false를 반환한다") {
                val userId = 1L
                val checkAmount = BigDecimal("50.00")
                
                every { pointRepository.findByUserId(userId) } returns null

                val result = balanceService.checkBalance(userId, checkAmount)

                result shouldBe false
            }
        }
        
        When("포인트 이력 조회를 요청받으면") {
            Then("사용자 검증 후 이력을 조회한다") {
                val userId = 1L
                val histories = listOf(
                    PointHistory.charge(userId, BigDecimal("100.00")),
                    PointHistory.usage(userId, BigDecimal("50.00"))
                )
                
                every { userService.existsById(userId) } returns true
                every { pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId) } returns histories

                val result = balanceService.getPointHistory(userId)

                // then - BalanceService의 책임: 사용자 검증 + 이력 조회
                result.size shouldBe 2
                result shouldBe histories
                
                verify(exactly = 1) { userService.existsById(userId) }
                verify(exactly = 1) { pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId) }
            }
            
            Then("존재하지 않는 사용자의 경우 예외를 발생시킨다") {
                val userId = 1L
                
                every { userService.existsById(userId) } returns false

                val exception = shouldThrow<UserNotFoundException> {
                    balanceService.getPointHistory(userId)
                }
                
                exception.message?.contains("존재하지 않는 사용자입니다") shouldBe true
            }
        }
    }
})
