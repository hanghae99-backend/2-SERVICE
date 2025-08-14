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
        key = "user:#userId",
        strategy = LockStrategy.SPIN,
        waitTimeoutMs = 2000L,
        retryIntervalMs = 50L,
        maxRetryCount = 40
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, amount: BigDecimal): Point {
        val currentPoint = pointRepository.findByUserId(userId)
            ?: run {
                // 포인트가 없는 경우 새로 생성 (동시성 안전하게)
                try {
                    val newPoint = Point.create(userId, BigDecimal.ZERO)
                    pointRepository.save(newPoint)
                } catch (e: Exception) {
                    // 다른 스레드에서 이미 생성한 경우, 다시 조회
                    logger.info("Point already created by another thread for user: $userId")
                    pointRepository.findByUserId(userId)
                        ?: throw IllegalStateException("포인트 생성 실패: $userId")
                }
            }

        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)

        val chargeType = pointHistoryTypeRepository.getChargeType()
        val history = PointHistory.charge(userId, amount, chargeType, "포인트 충전")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }
}
