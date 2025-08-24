package kr.hhplus.be.server.api.balance.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.domain.balance.exception.InvalidAmountException
import kr.hhplus.be.server.domain.common.BusinessRuleViolationException
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.config.TestDataConstants
import java.math.BigDecimal
import java.time.LocalDateTime

class ChargeBalanceUseCaseTest : DescribeSpec({

    describe("execute") {
        context("유효한 금액으로 충전할 때") {
            it("포인트를 충전하고 히스토리를 저장해야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val chargeBalanceUseCase = ChargeBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val chargeAmount = BigDecimal("15000")
                val currentPoint = Point.create(userId, BigDecimal("10000"))
                val expectedFinalAmount = BigDecimal("25000")
                
                val expectedResultPoint = Point.create(userId, BigDecimal("25000"))
                val chargeType = TestDataFixture.createPointHistoryType(
                    code = TestDataConstants.PointHistoryType.CHARGE.code,
                    name = TestDataConstants.PointHistoryType.CHARGE.name,
                    description = TestDataConstants.PointHistoryType.CHARGE.description
                )
                val history = PointHistory.charge(userId, chargeAmount, chargeType, "포인트 충전")
                
                every { pointRepository.findByUserId(userId) } returns Point.create(userId, BigDecimal("10000"))
                every { pointRepository.save(any()) } returns expectedResultPoint
                every { pointHistoryTypeRepository.getChargeType() } returns chargeType
                every { pointHistoryRepository.save(any()) } returns history

                // when
                val result = chargeBalanceUseCase.execute(userId, chargeAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("25000")
                verify { pointRepository.save(any()) }
                verify { pointHistoryRepository.save(any()) }
            }

        context("최소 충전 금액 미만으로 충전할 때") {
            it("도메인 모델에서 예외를 던져야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val chargeBalanceUseCase = ChargeBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val invalidAmount = BigDecimal("500") // 최소 1000원 미만
                val currentPoint = Point.create(userId, BigDecimal("5000"))
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                
                // when & then
                shouldThrow<BusinessRuleViolationException> {
                    chargeBalanceUseCase.execute(userId, invalidAmount)
                }
            }
        }

        context("최대 잔액 한도를 초과할 때") {
            it("도메인 모델에서 예외를 던져야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val eventPublisher = mockk<DomainEventPublisher>()
                val chargeBalanceUseCase = ChargeBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val currentPoint = Point.create(userId, BigDecimal("49000000")) // 최대 5000만원 근처
                val chargeAmount = BigDecimal("2000000") // 한도 초과하는 충전
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                
                // when & then
                shouldThrow<BusinessRuleViolationException> {
                    chargeBalanceUseCase.execute(userId, chargeAmount)
                }
            }
        }
        }

        context("새로운 사용자가 충전할 때") {
            it("새 포인트를 생성하고 충전해야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val eventPublisher = mockk<DomainEventPublisher>()
                val chargeBalanceUseCase = ChargeBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 2L
                val chargeAmount = BigDecimal("5000")
                val existingPoint = Point.create(userId, BigDecimal.ZERO) // 0원으로 시작
                val chargedPoint = mockk<Point>()
                val chargeType = TestDataFixture.createPointHistoryType(
                    code = TestDataConstants.PointHistoryType.CHARGE.code,
                    name = TestDataConstants.PointHistoryType.CHARGE.name,
                    description = TestDataConstants.PointHistoryType.CHARGE.description
                )
                val history = PointHistory.charge(userId, chargeAmount, chargeType, "포인트 충전")
                
                // 기존 포인트가 있는 것처럼 설정
                every { pointRepository.findByUserId(userId) } returns existingPoint
                every { pointRepository.save(any()) } returns chargedPoint
                every { chargedPoint.amount } returns BigDecimal("5000")
                every { pointHistoryTypeRepository.getChargeType() } returns chargeType
                every { pointHistoryRepository.save(any()) } returns history
                
                // when
                val result = chargeBalanceUseCase.execute(userId, chargeAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("5000")
            }
        }

        context("0원을 충전하려고 할 때") {
            it("도메인 모델에서 예외를 던져야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val chargeBalanceUseCase = ChargeBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val invalidAmount = BigDecimal.ZERO
                val currentPoint = Point.create(userId, BigDecimal("5000"))
                
                every { pointRepository.findByUserId(userId) } returns currentPoint
                
                // when & then
                shouldThrow<BusinessRuleViolationException> {
                    chargeBalanceUseCase.execute(userId, invalidAmount)
                }
            }
        }
    }
})
