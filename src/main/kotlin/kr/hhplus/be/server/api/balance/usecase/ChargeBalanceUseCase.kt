package kr.hhplus.be.server.api.balance.usecase

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
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
class ChargeBalanceUseCase(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
) {
    
    private val logger = LoggerFactory.getLogger(ChargeBalanceUseCase::class.java)
    
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
        logger.info("포인트 충전 시작 - userId: $userId, amount: $amount")
        
        val currentPoint = getOrCreatePoint(userId)
        
        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)
        
        saveChargeHistory(userId, amount)
        
        logger.info("포인트 충전 완료 - userId: $userId, 충전 후 잔액: ${savedPoint.amount}")
        return savedPoint
    }
    
    private fun getOrCreatePoint(userId: Long): Point {
        return pointRepository.findByUserId(userId) ?: run {
            val newPoint = Point.create(userId, BigDecimal.ZERO)
            pointRepository.save(newPoint)
        }
    }
    
    private fun saveChargeHistory(userId: Long, amount: BigDecimal) {
        val chargeType = pointHistoryTypeRepository.getChargeType()
        val history = PointHistory.charge(userId, amount, chargeType, "포인트 충전")
        pointHistoryRepository.save(history)
    }
}
