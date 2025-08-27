package kr.hhplus.be.server.internal.balance.usecase

import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class DeductBalanceUseCase(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DeductBalanceUseCase::class.java)
    }

    @LockGuard(
        key = "'balance:' + #userId",
        strategy = LockStrategy.SPIN,
        waitTimeoutMs = 3000L,
        retryIntervalMs = 100L,
        maxRetryCount = 30
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, amount: BigDecimal, description: String = "포인트 사용"): Point {
        return executeInternal(userId, amount, description)
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun executeInternal(userId: Long, amount: BigDecimal, description: String = "포인트 사용"): Point {
        logger.info("포인트 차감 시작 - userId: {}, amount: {}, description: {}", userId, amount, description)

        validateAmount(amount)

        val currentPoint = pointRepository.findByUserId(userId)
            ?: throw PointNotFoundException(userId)

        logger.debug("현재 잔액 확인 - userId: {}, currentBalance: {}", userId, currentPoint.amount)

        currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(currentPoint)

        saveUseHistory(userId, amount, description)

        logger.info(
            "포인트 차감 완료 - userId: {}, 차감액: {}, 차감 후 잔액: {}",
            userId, amount, savedPoint.amount
        )

        return savedPoint
    }

    private fun validateAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("차감 금액은 0보다 커야 합니다: $amount")
        }
    }

    private fun saveUseHistory(userId: Long, amount: BigDecimal, description: String) {
        try {
            val useType = pointHistoryTypeRepository.getUseType()
            val history = PointHistory.Companion.use(userId, amount, useType, description)
            pointHistoryRepository.save(history)

            logger.debug("포인트 사용 이력 저장 완료 - userId: {}, amount: {}", userId, amount)
        } catch (e: Exception) {
            logger.error("포인트 사용 이력 저장 실패 - userId: {}, amount: {}", userId, amount, e)
            throw e
        }
    }
}