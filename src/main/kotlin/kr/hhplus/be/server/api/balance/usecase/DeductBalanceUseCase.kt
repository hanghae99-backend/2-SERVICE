package kr.hhplus.be.server.api.balance.usecase

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import org.slf4j.LoggerFactory

@Service
class DeductBalanceUseCase(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
) {
    
    private val logger = LoggerFactory.getLogger(DeductBalanceUseCase::class.java)

    @LockGuard(
        key = "'balance:' + #userId",
        strategy = LockStrategy.SPIN,
        waitTimeoutMs = 3000L,
        retryIntervalMs = 100L,
        maxRetryCount = 30
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, amount: BigDecimal): Point {
        logger.info("포인트 차감 시작 - userId: $userId, amount: $amount")
        
        val currentPoint = pointRepository.findByUserId(userId)
            ?: throw PointNotFoundException(userId)

        val deductedPoint = currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(deductedPoint)
        
        saveUseHistory(userId, amount)
        
        logger.info("포인트 차감 완료 - userId: $userId, 차감 후 잔액: ${savedPoint.amount}")
        return savedPoint
    }
    
    private fun saveUseHistory(userId: Long, amount: BigDecimal) {
        val useType = pointHistoryTypeRepository.getUseType()
        val history = PointHistory.use(userId, amount, useType, "포인트 사용")
        pointHistoryRepository.save(history)
    }
}
