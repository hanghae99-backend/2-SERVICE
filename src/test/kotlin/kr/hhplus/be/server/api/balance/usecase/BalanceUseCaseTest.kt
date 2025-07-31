

package kr.hhplus.be.server.api.balance.usecase

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
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.global.lock.DistributedLock
import java.math.BigDecimal
import java.time.LocalDateTime

class BalanceUseCaseTest : DescribeSpec({
    
    val balanceService = mockk<BalanceService>()
    val userService = mockk<UserService>()
    val distributedLock = mockk<DistributedLock>()

    val balanceUseCase = BalanceUseCase(
        balanceService,
        userService,
        distributedLock
    )
    
    // DistributedLock executeWithLock 메서드의 기본 동작 설정 (파일 최상단에 선언)
    fun <T> setupDistributedLockMock(distributedLock: DistributedLock) {
        every {
            distributedLock.executeWithLock<T>(
                lockKey = any(),
                lockTimeoutMs = any(),
                waitTimeoutMs = any(),
                action = any()
            )
        } answers {
            val action = args[3] as () -> T
            action.invoke()
        }
    }

    // ...existing code...
    
    describe("chargeBalance") {
        context("유효한 사용자가 적절한 금액으로 충전할 때") {
            it("포인트를 충전해야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("10000")
                val chargedPoint = Point.create(userId, BigDecimal("15000"))
                
                setupDistributedLockMock<Point>(distributedLock)
                every { userService.existsById(userId) } returns true
                every { balanceService.chargeBalance(userId, chargeAmount) } returns chargedPoint
                
                // when
                val result = balanceUseCase.chargeBalance(userId, chargeAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("15000")
                verify { userService.existsById(userId) }
                verify { balanceService.chargeBalance(userId, chargeAmount) }
            }
        }
        
        context("존재하지 않는 사용자가 충전할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                val chargeAmount = BigDecimal("10000")
                
                setupDistributedLockMock<Point>(distributedLock)
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    balanceUseCase.chargeBalance(userId, chargeAmount)
                }
                
                verify { userService.existsById(userId) }
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
                every { balanceService.getBalance(userId) } returns point
                
                // when
                val result = balanceUseCase.getBalance(userId)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("10000")
                verify { userService.existsById(userId) }
                verify { balanceService.getBalance(userId) }
            }
        }
        
        context("존재하지 않는 사용자의 잔액 조회할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    balanceUseCase.getBalance(userId)
                }
                
                verify { userService.existsById(userId) }
            }
        }
    }
    
    describe("deductBalance") {
        context("충분한 잔액이 있는 사용자가 차감할 때") {
            it("포인트를 차감해야 한다") {
                // given
                val userId = 1L
                val deductAmount = BigDecimal("5000")
                val deductedPoint = Point.create(userId, BigDecimal("5000"))
                
                setupDistributedLockMock<Point>(distributedLock)
                every { userService.existsById(userId) } returns true
                every { balanceService.deductBalance(userId, deductAmount) } returns deductedPoint
                
                // when
                val result = balanceUseCase.deductBalance(userId, deductAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("5000")
                verify { userService.existsById(userId) }
                verify { balanceService.deductBalance(userId, deductAmount) }
            }
        }
        
        context("존재하지 않는 사용자가 차감할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                val deductAmount = BigDecimal("5000")
                
                setupDistributedLockMock<Point>(distributedLock)
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    balanceUseCase.deductBalance(userId, deductAmount)
                }
                
                verify { userService.existsById(userId) }
            }
        }
    }
    
    describe("deductBalanceInternal") {
        context("내부 호출로 포인트를 차감할 때") {
            it("사용자 검증 없이 포인트를 차감해야 한다") {
                // given
                val userId = 1L
                val deductAmount = BigDecimal("5000")
                val deductedPoint = Point.create(userId, BigDecimal("5000"))
                
                every { balanceService.deductBalance(userId, deductAmount) } returns deductedPoint
                
                // when
                val result = balanceUseCase.deductBalanceInternal(userId, deductAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("5000")
                verify { balanceService.deductBalance(userId, deductAmount) }
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
                
                every { userService.existsById(userId) } returns true
                every { balanceService.getPointHistory(userId) } returns histories
                
                // when
                val result = balanceUseCase.getPointHistory(userId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
                verify { userService.existsById(userId) }
                verify { balanceService.getPointHistory(userId) }
            }
        }
        
        context("존재하지 않는 사용자의 이력 조회할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    balanceUseCase.getPointHistory(userId)
                }
                
                verify { userService.existsById(userId) }
            }
        }
    }
})