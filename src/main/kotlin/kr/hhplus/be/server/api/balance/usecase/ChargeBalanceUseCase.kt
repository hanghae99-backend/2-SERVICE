package kr.hhplus.be.server.api.balance.usecase

import kr.hhplus.be.server.api.balance.dto.BalanceDto
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.event.BalanceChargedEvent
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.lock.LockGuard
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ChargeBalanceUseCase(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
    private val eventPublisher: DomainEventPublisher
) {

    @Transactional
    @ValidateUserId
    @LockGuard(key = "balance:#userId")
    fun execute(userId: Long, amount: BigDecimal): Point {
        val currentPoint = pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)

        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)

        val chargeType = pointHistoryTypeRepository.getChargeType()
        val history = PointHistory.charge(userId, amount, chargeType, "포인트 충전")
        pointHistoryRepository.save(history)

        val event = BalanceChargedEvent(
            userId = userId,
            amount = amount,
            newBalance = savedPoint.amount
        )
        eventPublisher.publish(event)

        return savedPoint
    }
}
