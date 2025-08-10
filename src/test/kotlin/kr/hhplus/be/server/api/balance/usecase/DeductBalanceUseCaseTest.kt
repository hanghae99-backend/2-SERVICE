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
import kr.hhplus.be.server.domain.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.domain.balance.exception.InsufficientBalanceException
import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
import java.math.BigDecimal
import java.time.LocalDateTime

class DeductBalanceUseCaseTest : DescribeSpec({

    describe("execute") {
        context("충분한 잔액이 있는 사용자가 차감할 때") {
            it("포인트를 차감하고 히스토리를 저장해야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val deductBalanceUseCase = DeductBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val deductAmount = BigDecimal("5000")
                val currentPoint = Point.create(userId, BigDecimal("10000"))
                val expectedFinalAmount = BigDecimal("5000")
                
                val expectedResultPoint = Point.create(userId, BigDecimal("5000"))
                val useType = PointHistoryType("USE", "사용", "포인트 사용", true, LocalDateTime.now())
                val history = PointHistory.use(userId, deductAmount, useType, "포인트 사용")
                
                every { pointRepository.findByUserIdWithPessimisticLock(userId) } returns Point.create(userId, BigDecimal("10000"))
                every { pointRepository.save(any()) } returns expectedResultPoint
                every { pointHistoryTypeRepository.getUseType() } returns useType
                every { pointHistoryRepository.save(any()) } returns history

                // when
                val result = deductBalanceUseCase.execute(userId, deductAmount)
                
                // then
                result shouldNotBe null
                result.amount shouldBe BigDecimal("5000")
            }

        context("음수 금액을 차감하려고 할 때") {
            it("도메인 모델에서 예외를 던져야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val deductBalanceUseCase = DeductBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val negativeAmount = BigDecimal("-1000")
                val currentPoint = Point.create(userId, BigDecimal("10000"))
                
                every { pointRepository.findByUserIdWithPessimisticLock(userId) } returns currentPoint
                
                // when & then
                shouldThrow<InvalidPointAmountException> {
                    deductBalanceUseCase.execute(userId, negativeAmount)
                }
            }
        }
        }

        context("포인트가 없는 사용자가 차감할 때") {
            it("PointNotFoundException을 던져야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val deductBalanceUseCase = DeductBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val deductAmount = BigDecimal("5000")
                
                every { pointRepository.findByUserIdWithPessimisticLock(userId) } returns null
                
                // when & then
                shouldThrow<PointNotFoundException> {
                    deductBalanceUseCase.execute(userId, deductAmount)
                }
            }
        }

        context("잔액이 부족한 사용자가 차감할 때") {
            it("도메인 모델에서 예외를 던져야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val deductBalanceUseCase = DeductBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val deductAmount = BigDecimal("15000")
                val currentPoint = Point.create(userId, BigDecimal("10000"))
                
                every { pointRepository.findByUserIdWithPessimisticLock(userId) } returns currentPoint
                
                // when & then
                shouldThrow<InsufficientBalanceException> {
                    deductBalanceUseCase.execute(userId, deductAmount)
                }
            }
        }

        context("0원을 차감하려고 할 때") {
            it("도메인 모델에서 예외를 던져야 한다") {
                // given
                val pointRepository = mockk<PointRepository>()
                val pointHistoryRepository = mockk<PointHistoryRepository>()
                val pointHistoryTypeRepository = mockk<PointHistoryTypePojoRepository>()
                val deductBalanceUseCase = DeductBalanceUseCase(
                    pointRepository,
                    pointHistoryRepository,
                    pointHistoryTypeRepository,
                )
                val userId = 1L
                val invalidAmount = BigDecimal.ZERO
                val currentPoint = Point.create(userId, BigDecimal("10000"))
                
                every { pointRepository.findByUserIdWithPessimisticLock(userId) } returns currentPoint
                
                // when & then
                shouldThrow<InvalidPointAmountException> {
                    deductBalanceUseCase.execute(userId, invalidAmount)
                }
            }
        }
    }
})
