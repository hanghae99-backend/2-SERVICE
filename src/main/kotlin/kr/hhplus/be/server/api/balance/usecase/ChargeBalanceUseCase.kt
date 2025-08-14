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
        strategy = LockStrategy.SIMPLE,
        lockTimeoutMs = 45000L,            // 락 유지 시간 45초
        waitTimeoutMs = 20000L,            // 대기 시간 20초  
        retryIntervalMs = 100L,            // 재시도 간격 100ms
        maxRetryCount = 200                // 재시도 횟수 200번
    )
    @ValidateUserId
    fun execute(userId: Long, amount: BigDecimal): Point {
        logger.debug("포인트 충전 시작 - userId: $userId, amount: $amount")
        
        // 비즈니스 로직 검증 (락 외부에서)
        validateChargeAmount(amount)
        
        // 락 내에서 단순한 작업만 수행
        return performChargeTransaction(userId, amount)
    }
    
    private fun validateChargeAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("충전 금액은 0보다 커야 합니다")
        }
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    private fun performChargeTransaction(userId: Long, amount: BigDecimal): Point {
        // 1. 포인트 조회 또는 생성 (원자적 처리)
        val currentPoint = getOrCreatePoint(userId)
        logger.debug("현재 포인트: ${currentPoint.amount}")
        
        // 2. 포인트 충전
        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)
        logger.debug("충전 후 포인트: ${savedPoint.amount}")
        
        // 3. 히스토리 기록
        val chargeType = pointHistoryTypeRepository.getChargeType()
        val history = PointHistory.charge(userId, amount, chargeType, "포인트 충전")
        pointHistoryRepository.save(history)
        
        logger.debug("포인트 충전 완료 - userId: $userId, 최종 잔액: ${savedPoint.amount}")
        return savedPoint
    }
    
    /**
     * 포인트를 조회하거나 없으면 안전하게 생성
     */
    private fun getOrCreatePoint(userId: Long): Point {
        // 먼저 조회 시도
        val existingPoint = pointRepository.findByUserId(userId)
        if (existingPoint != null) {
            return existingPoint
        }
        
        // 포인트가 없으면 생성 시도
        return try {
            logger.debug("사용자 $userId 포인트 생성 중...")
            val newPoint = Point.create(userId, BigDecimal.ZERO)
            val savedPoint = pointRepository.save(newPoint)
            logger.debug("사용자 $userId 포인트 생성 완료")
            savedPoint
        } catch (e: Exception) {
            // 다른 스레드에서 이미 생성한 경우 재조회
            logger.info("다른 스레드에서 포인트 생성됨 - userId: $userId, 재조회 중...")
            pointRepository.findByUserId(userId)
                ?: throw IllegalStateException("포인트 생성 실패: $userId")
        }
    }
}
