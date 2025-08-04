package kr.hhplus.be.server.domain.balance.service

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.event.BalanceChargedEvent
import kr.hhplus.be.server.domain.balance.event.BalanceDeductedEvent
import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
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
@Transactional(readOnly = true)
class BalanceService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
    private val eventPublisher: DomainEventPublisher
) {

    @Transactional
    @ValidateUserId
    @LockGuard(key = "balance:#userId")
    fun chargeBalance(userId: Long, amount: BigDecimal): Point {
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

    @Transactional
    @ValidateUserId
    @LockGuard(key = "balance:#userId")
    fun deductBalance(userId: Long, amount: BigDecimal): Point {
        // 현재 포인트 조회
        val currentPoint = pointRepository.findByUserId(userId)
            ?: throw PointNotFoundException("포인트 정보를 찾을 수 없습니다")

        // 도메인 모델의 deduct 메서드로 검증 및 차감
        val deductedPoint = currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(deductedPoint)

        // 히스토리 저장
        val useType = pointHistoryTypeRepository.getUseType()
        val history = PointHistory.use(userId, amount, useType, "포인트 사용")
        pointHistoryRepository.save(history)

        // 차감 이벤트 발행
        val event = BalanceDeductedEvent(
            userId = userId,
            amount = amount,
            remainingBalance = savedPoint.amount
        )
        eventPublisher.publish(event)

        return savedPoint
    }


    @ValidateUserId
    fun getBalance(userId: Long): Point {
        return pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
    }

    @ValidateUserId
    fun getPointHistory(userId: Long): List<PointHistory> {
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
}