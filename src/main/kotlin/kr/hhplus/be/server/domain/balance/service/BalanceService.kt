package kr.hhplus.be.server.domain.balance.service

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.balance.rules.BalanceBusinessRules
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
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
    private val pointHistoryRepository: PointHistoryRepository,
    private val reservationRepository: ReservationRepository
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
        val activeStatusCodes = listOf(
            ReservationStatusType.TEMPORARY,
            ReservationStatusType.CONFIRMED
        )
        return reservationRepository.findByUserIdAndStatusCodeInOrderByReservedAtDesc(
            userId = userId,
            statusCodes = activeStatusCodes
        ).size
    }
    
    /**
     * 잔고 복원 (결제 실패 시 사용)
     */
    @Transactional
    @ValidateUserId
    fun restoreBalance(userId: Long, amount: BigDecimal, description: String): Point {
        logger.info("잔고 복원 요청 - userId: {}, amount: {}", userId, amount)
        
        // 1. 현재 잔고 조회
        val currentPoint = pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
        
        // 2. 잔고 복원
        currentPoint.charge(amount)
        val savedPoint = pointRepository.save(currentPoint)
        
        // 3. 이력 저장
        val refundType = PointHistoryType(
            code = "REFUND",
            name = "환불",
            description = "결제 실패로 인한 잔고 복원",
            category = "REFUND"
        )
        val history = PointHistory.refund(
            userId = userId,
            amount = amount,
            refundType = refundType,
            description = description
        )
        pointHistoryRepository.save(history)
        
        logger.info("잔고 복원 완료 - userId: {}, amount: {}, newBalance: {}", 
                   userId, amount, savedPoint.amount)
        
        return savedPoint
    }
}