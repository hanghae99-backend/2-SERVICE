package kr.hhplus.be.server.balance.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.balance.exception.PointNotFoundException
import kr.hhplus.be.server.balance.repository.PointHistoryRepository
import kr.hhplus.be.server.balance.repository.PointRepository
import kr.hhplus.be.server.user.exception.UserNotFoundException
import kr.hhplus.be.server.user.service.UserService
import java.math.BigDecimal

class BalanceServiceTest : DescribeSpec({
    
    val pointRepository = mockk<PointRepository>()
    val pointHistoryRepository = mockk<PointHistoryRepository>()
    val userService = mockk<UserService>()
    val balanceService = BalanceService(pointRepository, pointHistoryRepository, userService)
    
    describe("chargeBalance") {
        context("유효한 사용자가 적절한 금액으로 충전할 때") {
            it("포인트를 충전하고 히스토리를 저장해야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("10000")
                val currentPoint = Point.create(userId, BigDecimal("5000"))
                val chargedPoint = Point.create(userId, BigDecimal("15000"))
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns currentPoint
                every { pointRepository.save(any()) } returns chargedPoint
                every { pointHistoryRepository.save(any()) } returns mockk()
                
                // when
                val result = balanceService.chargeBalance(userId, chargeAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("15000")
            }
        }
        
        context("존재하지 않는 사용자가 충전할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                val chargeAmount = BigDecimal("10000")
                
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    balanceService.chargeBalance(userId, chargeAmount)
                }
            }
        }
        
        context("최소 충전 금액보다 적은 금액으로 충전할 때") {
            it("InvalidPointAmountException을 던져야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("500") // 최소 1000원보다 적음
                val currentPoint = Point.create(userId, BigDecimal("5000"))
                
                every { userService.existsById(userId) } returns true
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
                
                every { userService.existsById(userId) } returns true
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
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns null
                every { pointRepository.save(any()) } returns newPoint
                every { pointHistoryRepository.save(any()) } returns mockk()
                
                // when
                val result = balanceService.chargeBalance(userId, chargeAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("10000")
            }
        }
    }
    
    describe("getBalance") {
        context("기존 포인트가 있는 사용자의 잔액 조회할 때") {
            it("해당 사용자의 포인트를 반환해야 한다") {
                // given
                val userId = 1L
                val point = Point.create(userId, BigDecimal("10000"))
                
                every { userService.existsById(userId) } returns true
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
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns null
                
                // when
                val result = balanceService.getBalance(userId)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal.ZERO
            }
        }
        
        context("존재하지 않는 사용자의 잔액 조회할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    balanceService.getBalance(userId)
                }
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
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns currentPoint
                every { pointRepository.save(any()) } returns deductedPoint
                every { pointHistoryRepository.save(any()) } returns mockk()
                
                // when
                val result = balanceService.deductBalance(userId, deductAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("5000")
            }
        }
        
        context("포인트가 없는 사용자가 차감할 때") {
            it("PointNotFoundException을 던져야 한다") {
                // given
                val userId = 1L
                val deductAmount = BigDecimal("5000")
                
                every { userService.existsById(userId) } returns true
                every { pointRepository.findByUserId(userId) } returns null
                
                // when & then
                shouldThrow<PointNotFoundException> {
                    balanceService.deductBalance(userId, deductAmount)
                }
            }
        }
        
        context("존재하지 않는 사용자가 차감할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                val deductAmount = BigDecimal("5000")
                
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
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
                val histories = listOf(
                    PointHistory.charge(userId, BigDecimal("10000"), "포인트 충전"),
                    PointHistory.use(userId, BigDecimal("5000"), "포인트 사용")
                )
                
                every { userService.existsById(userId) } returns true
                every { pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId) } returns histories
                
                // when
                val result = balanceService.getPointHistory(userId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
            }
        }
        
        context("존재하지 않는 사용자의 이력 조회할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    balanceService.getPointHistory(userId)
                }
            }
        }
    }
})
