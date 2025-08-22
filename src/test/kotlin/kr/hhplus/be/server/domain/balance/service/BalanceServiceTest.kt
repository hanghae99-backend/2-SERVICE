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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class BalanceServiceTest : DescribeSpec({
    
    val pointRepository = mockk<PointRepository>()
    val pointHistoryRepository = mockk<PointHistoryRepository>()
    
    val balanceService = BalanceService(
        pointRepository,
        pointHistoryRepository
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
                verify { pointRepository.findByUserId(userId) }
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
                verify { pointRepository.findByUserId(userId) }
            }
        }
    }
    
    describe("getPointHistory") {
        context("사용자의 포인트 이력을 조회할 때") {
            it("이력 목록을 반환해야 한다") {
                // given
                val userId = 1L
                val chargeType = PointHistoryType(PointHistoryType.CHARGE, "충전", "포인트 충전")
                val useType = PointHistoryType(PointHistoryType.USE, "사용", "포인트 사용")
                val histories = listOf(
                    PointHistory.charge(userId, BigDecimal("10000"), chargeType, "충전", BigDecimal("10000")),
                    PointHistory.use(userId, BigDecimal("3000"), useType, "사용", BigDecimal("7000"))
                )
                
                every { pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId) } returns histories
                
                // when
                val result = balanceService.getPointHistory(userId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
                verify { pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId) }
            }
        }
    }
    
    describe("getTodayChargeAmount") {
        context("오늘 충전 금액을 조회할 때") {
            it("오늘 충전한 총 금액을 반환해야 한다") {
                // given
                val userId = 1L
                val today = LocalDate.now()
                val todayChargeAmount = BigDecimal("50000")
                
                every { pointHistoryRepository.findChargeAmountByUserIdAndDate(userId, today) } returns todayChargeAmount
                
                // when
                val result = balanceService.getTodayChargeAmount(userId)
                
                // then
                result shouldBe BigDecimal("50000")
                verify { pointHistoryRepository.findChargeAmountByUserIdAndDate(userId, today) }
            }
        }
    }
})