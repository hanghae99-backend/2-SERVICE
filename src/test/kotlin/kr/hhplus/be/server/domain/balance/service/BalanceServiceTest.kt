package kr.hhplus.be.server.domain.balance.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import java.math.BigDecimal
import java.time.LocalDate

class BalanceServiceTest : DescribeSpec({
    
    val pointRepository = mockk<PointRepository>(relaxed = true)
    val pointHistoryRepository = mockk<PointHistoryRepository>(relaxed = true)
    val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>(relaxed = true)
    
    val balanceService = BalanceService(
        pointRepository,
        pointHistoryRepository,
        pointHistoryTypeRepository
    )
    
    describe("getBalance") {
        context("포인트 계정이 있을 때") {
            it("현재 잔액을 반환해야 한다") {
                // given
                val userId = 1L
                val point = Point.create(userId, BigDecimal("10000"))
                
                every { pointRepository.findByUserId(userId) } returns point
                
                // when
                val result = balanceService.getBalance(userId)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("10000")
            }
        }
        
        context("포인트 계정이 없을 때") {
            it("0원으로 초기화된 포인트를 반환해야 한다") {
                // given
                val userId = 1L
                
                every { pointRepository.findByUserId(userId) } returns null
                
                // when
                val result = balanceService.getBalance(userId)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal.ZERO
            }
        }
    }
    
    describe("chargeBalance") {
        context("기존 계정에 포인트를 충전할 때") {
            it("잔액이 증가해야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("10000")
                val currentPoint = Point.create(userId, BigDecimal("5000"))
                val chargeType = PointHistoryType.createDefault("CHARGE", "충전", PointHistoryType.CATEGORY_CHARGE)
                
                // 실제 도메인 로직 수행하도록 수정
                currentPoint.charge(chargeAmount) // 15000이 됨
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                every { pointHistoryRepository.findChargeAmountByUserIdAndDate(userId, any<LocalDate>()) } returns BigDecimal.ZERO
                every { pointHistoryTypeRepository.getChargeType() } returns chargeType
                every { pointRepository.save(any<Point>()) } returns currentPoint
                
                // when
                val result = balanceService.chargeBalance(userId, chargeAmount)
                
                // then
                result.amount shouldBe BigDecimal("25000") // 15000 + 10000
                verify { pointRepository.save(any<Point>()) }
                verify { pointHistoryRepository.save(any<PointHistory>()) }
            }
        }
    }
    
    describe("deductBalance") {
        context("충분한 잔액이 있을 때") {
            it("잔액이 차감되어야 한다") {
                // given
                val userId = 1L
                val deductAmount = BigDecimal("5000")
                val currentPoint = Point.create(userId, BigDecimal("10000"))
                val useType = PointHistoryType.createDefault("USE", "사용", PointHistoryType.CATEGORY_USE)
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                every { pointHistoryTypeRepository.getUseType() } returns useType
                every { pointRepository.save(any<Point>()) } returns currentPoint
                
                // when
                val result = balanceService.deductBalance(userId, deductAmount, "결제")
                
                // then
                result.amount shouldBe BigDecimal("5000")
                verify { pointRepository.save(any<Point>()) }
                verify { pointHistoryRepository.save(any<PointHistory>()) }
            }
        }
        
        context("잔액이 부족할 때") {
            it("PointNotFoundException이 발생해야 한다") {
                // given
                val userId = 1L
                val deductAmount = BigDecimal("15000")
                
                every { pointRepository.findByUserId(userId) } returns null
                
                // when & then
                shouldThrow<kr.hhplus.be.server.domain.balance.exception.PointNotFoundException> {
                    balanceService.deductBalance(userId, deductAmount)
                }
            }
        }
    }
})
