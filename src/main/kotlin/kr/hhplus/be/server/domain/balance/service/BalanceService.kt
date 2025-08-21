package kr.hhplus.be.server.domain.balance.service

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.common.BalanceBusinessRules
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class BalanceService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(BalanceService::class.java)
    }
    
    @ValidateUserId
    fun getBalance(userId: Long): Point {
        logger.debug("잔액 조회 - userId: {}", userId)
        return pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
    }

    @ValidateUserId
    fun getPointHistory(userId: Long): List<PointHistory> {
        logger.debug("포인트 이력 조회 - userId: {}", userId)
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
    
    /**
     * 일일 충전 금액 조회
     */
    @ValidateUserId
    fun getTodayChargeAmount(userId: Long): BigDecimal {
        val today = LocalDate.now()
        return pointHistoryRepository.findChargeAmountByUserIdAndDate(userId, today)
            ?: BigDecimal.ZERO
    }
    
    /**
     * 충전 전 비즈니스 규칙 검증
     */
    @ValidateUserId
    fun validateChargeRequest(userId: Long, chargeAmount: BigDecimal) {
        // 1. 충전 금액 범위 검증
        BalanceBusinessRules.validateChargeAmount(chargeAmount)
        
        // 2. 일일 충전 한도 검증
        val todayChargeAmount = getTodayChargeAmount(userId)
        BalanceBusinessRules.validateDailyChargeLimit(userId, todayChargeAmount, chargeAmount)
        
        // 3. 최대 잔액 한도 검증
        val currentBalance = getBalance(userId).amount
        BalanceBusinessRules.validateBalanceLimit(currentBalance, chargeAmount)
        
        logger.info("충전 사전 검증 완료 - userId: {}, amount: {}, todayTotal: {}", 
                   userId, chargeAmount, todayChargeAmount)
    }
    
    /**
     * 사용자의 활성 예약 수 조회 (동시성 제어용)
     */
    @ValidateUserId
    fun getActiveReservationCount(userId: Long): Int {
        // 이 버전에서는 단순하게 0을 반환
        // 실제 구현에서는 ReservationRepository에서 조회 필요
        return 0
    }
}