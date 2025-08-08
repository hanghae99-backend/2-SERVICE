package kr.hhplus.be.server.api.balance.usecase

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.event.BalanceChargedEvent
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.event.DomainEventPublisher
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
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
    
    companion object {
        const val MAX_RETRIES = 10
        const val BACKOFF_MILLIS = 50L
    }

    @Transactional
    @ValidateUserId
    fun execute(userId: Long, amount: BigDecimal): Point {
        // 낙관적 락 충돌 시 재시도 로직
        repeat(MAX_RETRIES) { attempt ->
            try {
                return executeChargeInternal(userId, amount)
            } catch (e: OptimisticLockingFailureException) {
                logger.warn("Optimistic lock failure on charge attempt ${attempt + 1} for user: $userId")
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(BACKOFF_MILLIS * (attempt + 1)) // 지수 백오프
                } else {
                    throw IllegalStateException("포인트 충전 중 동시성 충돌이 지속되고 있습니다. 잠시 후 다시 시도해주세요.")
                }
            }
        }
        throw IllegalStateException("최대 재시도 횟수를 초과했습니다.")
    }
    
    private fun executeChargeInternal(userId: Long, amount: BigDecimal): Point {
        val currentPoint = pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)

        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)

        val chargeType = pointHistoryTypeRepository.getChargeType()
        val history = PointHistory.charge(userId, amount, chargeType, "포인트 충전")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }
}
