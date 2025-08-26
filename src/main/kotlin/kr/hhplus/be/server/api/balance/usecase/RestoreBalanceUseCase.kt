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

/**
 * 잔고 복원 UseCase - 결제 실패 시 차감된 잔고를 복원
 */
@Service
class RestoreBalanceUseCase(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
) {
    
    private val logger = LoggerFactory.getLogger(RestoreBalanceUseCase::class.java)
    
    @LockGuard(
        key = "'balance:' + #userId",
        strategy = LockStrategy.SPIN,
        lockTimeoutMs = 3000L,
        waitTimeoutMs = 15000L,
        retryIntervalMs = 30L,
        maxRetryCount = 200
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, amount: BigDecimal, reason: String, paymentId: Long): Point {
        logger.info("잔고 복원 시작 - userId: {}, amount: {}, reason: {}, paymentId: {}", userId, amount, reason, paymentId)
        
        val currentPoint = pointRepository.findByUserId(userId)
            ?: throw IllegalStateException("사용자의 포인트 정보를 찾을 수 없습니다: $userId")
        
        // 잔고 복원 (충전과 동일한 로직)
        currentPoint.charge(amount)
        val savedPoint = pointRepository.save(currentPoint)
        
        // 복원 이력 저장
        saveRestoreHistory(userId, amount, reason, paymentId)
        
        logger.info("잔고 복원 완료 - userId: {}, 복원 후 잔액: {}, paymentId: {}", userId, savedPoint.amount, paymentId)
        return savedPoint
    }
    
    private fun saveRestoreHistory(userId: Long, amount: BigDecimal, reason: String, paymentId: Long) {
        val chargeType = pointHistoryTypeRepository.getChargeType()
        val history = PointHistory.charge(
            userId = userId,
            amount = amount,
            chargeType = chargeType,
            description = "결제 실패로 인한 잔고 복원 - paymentId: $paymentId, 사유: $reason"
        )
        pointHistoryRepository.save(history)
    }
}