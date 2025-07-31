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
        pointHistoryTypeRepository,
        eventPublisher
    )
    
    describe("chargeBalance") {
        context("유효한 사용자가 적절한 금액으로 충전할 때") {
            it("포인트를 충전하고 히스토리를 저장해야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("10000")
                val currentPoint = Point.create(userId, BigDecimal("5000"))
                val chargedPoint = Point.create(userId, BigDecimal("15000"))
                val chargeType = PointHistoryType("CHARGE", "충전", "포인트 충전", true, LocalDateTime.now())
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                every { pointRepository.save(any()) } returns chargedPoint
                every { pointHistoryTypeRepository.getChargeType() } returns chargeType
                every { pointHistoryRepository.save(any()) } returns mockk()
                justRun { eventPublisher.publish(any()) }
                
                // when
                val result = balanceService.chargeBalance(userId, chargeAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("15000")
                verify { pointRepository.save(any()) }
                verify { pointHistoryRepository.save(any()) }
            }
        }
        
        context("최소 충전 금액보다 적은 금액으로 충전할 때") {
            it("InvalidPointAmountException을 던져야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("500") // 최소 1000원보다 적음
                val currentPoint = Point.create(userId, BigDecimal("5000"))
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                
                // when & then
                shouldThrow<InvalidPointAmountException> {
                    balanceService.chargeBalance(userId, chargeAmount)
                }
            }
        }
        
        context("최대 잔액 한도를 초과하는 금액으로 충전할 때") {
            it("InvalidPointAmountException을 던져야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("10000000")
                val currentPoint = Point.create(userId, BigDecimal("45000000"))
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                
                // when & then
                shouldThrow<InvalidPointAmountException> {
                    balanceService.chargeBalance(userId, chargeAmount)
                }
            }
        }
        
        context("신규 사용자가 충전할 때") {
            it("새로운 포인트를 생성하고 충전해야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("10000")
                val newPoint = Point.create(userId, BigDecimal("10000"))
                val chargeType = PointHistoryType("CHARGE", "충전", "포인트 충전", true, LocalDateTime.now())
                
                every { pointRepository.findByUserId(userId) } returns null
                every { pointRepository.save(any()) } returns newPoint
                every { pointHistoryTypeRepository.getChargeType() } returns chargeType
                every { pointHistoryRepository.save(any()) } returns mockk()
                justRun { eventPublisher.publish(any()) }
                
                // when
                val result = balanceService.chargeBalance(userId, chargeAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("10000")
                verify { pointRepository.save(any()) }
                verify { pointHistoryRepository.save(any()) }
            }
        }
    }
    
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
    
    describe("deductBalance") {
        context("충분한 잔액이 있는 사용자가 차감할 때") {
            it("포인트를 차감하고 히스토리를 저장해야 한다") {
                // given
                val userId = 1L
                val deductAmount = BigDecimal("5000")
                val currentPoint = Point.create(userId, BigDecimal("10000"))
                val deductedPoint = Point.create(userId, BigDecimal("5000"))
                val useType = PointHistoryType("USE", "사용", "포인트 사용", true, LocalDateTime.now())
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                every { pointRepository.save(any()) } returns deductedPoint
                every { pointHistoryTypeRepository.getUseType() } returns useType
                every { pointHistoryRepository.save(any()) } returns mockk()
                justRun { eventPublisher.publish(any()) }
                
                // when
                val result = balanceService.deductBalance(userId, deductAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("5000")
                verify { pointRepository.save(any()) }
                verify { pointHistoryRepository.save(any()) }
            }
        }
        
        context("포인트가 없는 사용자가 차감할 때") {
            it("PointNotFoundException을 던져야 한다") {
                // given
                val userId = 1L
                val deductAmount = BigDecimal("5000")
                
                every { pointRepository.findByUserId(userId) } returns null
                
                // when & then
                shouldThrow<PointNotFoundException> {
                    balanceService.deductBalance(userId, deductAmount)
                }
            }
        }
        
        context("잔액이 부족한 사용자가 차감할 때") {
            it("InvalidPointAmountException을 던져야 한다") {
                // given
                val userId = 1L
                val deductAmount = BigDecimal("15000")
                val currentPoint = Point.create(userId, BigDecimal("10000"))
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                
                // when & then
                shouldThrow<InsufficientBalanceException> {
                    balanceService.deductBalance(userId, deductAmount)
                }
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