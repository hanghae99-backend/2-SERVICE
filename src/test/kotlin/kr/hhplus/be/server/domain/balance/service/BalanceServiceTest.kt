package kr.hhplus.be.server.domain.balance.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.justRun
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
import kr.hhplus.be.server.domain.balance.exception.InsufficientBalanceException
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.global.event.DomainEventPublisher
import java.math.BigDecimal
import java.time.LocalDateTime

class BalanceServiceTest : DescribeSpec({
    
    val pointRepository = mockk<PointRepository>()
    val pointHistoryRepository = mockk<PointHistoryRepository>()
    val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
    val eventPublisher = mockk< DomainEventPublisher>()
    val balanceService = BalanceService(
        pointRepository,
        pointHistoryRepository,
    )
    
    describe("getBalance") {
        context("기존 포인트가 있는 사용자의 잔액 조회할 때") {
            it("해당 사용자의 포인트를 반환해야 한다") {
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
        
        context("포인트가 없는 사용자의 잔액 조회할 때") {
            it("0원 포인트를 반환해야 한다") {
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

    describe("getPointHistory") {
        context("포인트 이력이 있는 사용자의 이력 조회할 때") {
            it("해당 사용자의 포인트 이력을 반환해야 한다") {
                // given
                val userId = 1L
                val chargeType = PointHistoryType("CHARGE", "충전", "포인트 충전", true, LocalDateTime.now())
                val useType = PointHistoryType("USE", "사용", "포인트 사용", true, LocalDateTime.now())
                val histories = listOf(
                    PointHistory.charge(userId, BigDecimal("10000"), chargeType, "포인트 충전"),
                    PointHistory.use(userId, BigDecimal("5000"), useType, "포인트 사용")
                )
                
                every { pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId) } returns histories
                
                // when
                val result = balanceService.getPointHistory(userId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
            }
        }
        
        context("포인트 이력이 없는 사용자의 이력 조회할 때") {
            it("빈 리스트를 반환해야 한다") {
                // given
                val userId = 1L
                val histories = emptyList<PointHistory>()
                
                every { pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId) } returns histories
                
                // when
                val result = balanceService.getPointHistory(userId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 0
            }
        }
    }
})