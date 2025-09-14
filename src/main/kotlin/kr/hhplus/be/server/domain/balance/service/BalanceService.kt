package kr.hhplus.be.server.domain.balance.service

import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.balance.rules.BalanceBusinessRules
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.springframework.orm.ObjectOptimisticLockingFailureException
import kotlin.random.Random
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class BalanceService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository
) {
    
    @ValidateUserId
    fun getBalance(userId: Long): Point {
        return pointRepository.findByUserId(userId) ?: Point.create(userId, BigDecimal.ZERO)
    }

    @ValidateUserId
    fun getPointHistory(userId: Long): List<PointHistory> {
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
    
    @ValidateUserId
    fun getTodayChargeAmount(userId: Long): BigDecimal {
        val today = LocalDate.now()
        return pointHistoryRepository.findChargeAmountByUserIdAndDate(userId, today) ?: BigDecimal.ZERO
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun chargeBalance(userId: Long, amount: BigDecimal): Point {
        return chargeBalanceWithRetry(userId, amount, 0)
    }
    
    private fun chargeBalanceWithRetry(userId: Long, amount: BigDecimal, retryCount: Int): Point {
        try {
            validateChargeAmountInTransaction(userId, amount)
            
            val currentPoint = getOrCreatePoint(userId)
            currentPoint.charge(amount)
            val savedPoint = pointRepository.save(currentPoint)
            
            saveChargeHistory(userId, amount)
            return savedPoint
        } catch (e: ObjectOptimisticLockingFailureException) {
            if (retryCount < 3) {
                Thread.sleep(Random.nextLong(10, 50))
                return chargeBalanceWithRetry(userId, amount, retryCount + 1)
            } else {
                throw e
            }
        }
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun deductBalance(userId: Long, amount: BigDecimal, description: String = "포인트 사용"): Point {
        validateDeductAmount(amount)

        val currentPoint = pointRepository.findByUserId(userId) ?: throw PointNotFoundException(userId)
        currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(currentPoint)

        saveDeductHistory(userId, amount, description)
        return savedPoint
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun restoreBalance(userId: Long, amount: BigDecimal, description: String): Point {
        val currentPoint = getOrCreatePoint(userId)
        currentPoint.charge(amount)
        val savedPoint = pointRepository.save(currentPoint)
        
        val refundType = PointHistoryType(
            code = "REFUND",
            name = "환불",
            description = "결제 실패로 인한 잔고 복원",
            category = "REFUND"
        )
        val history = PointHistory.refund(userId, amount, refundType, description)
        pointHistoryRepository.save(history)
        
        return savedPoint
    }
    
    private fun validateChargeAmountInTransaction(userId: Long, chargeAmount: BigDecimal) {
        BalanceBusinessRules.validateChargeAmount(chargeAmount)
        
        val today = LocalDate.now()
        val todayChargeAmount = pointHistoryRepository.findChargeAmountByUserIdAndDate(userId, today) ?: BigDecimal.ZERO
        BalanceBusinessRules.validateDailyChargeLimit(userId, todayChargeAmount, chargeAmount)
        
        val currentBalance = pointRepository.findByUserId(userId)?.amount ?: BigDecimal.ZERO
        BalanceBusinessRules.validateBalanceLimit(currentBalance, chargeAmount)
    }
    
    private fun validateDeductAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("차감 금액은 0보다 커야 합니다: $amount")
        }
    }
    
    private fun getOrCreatePoint(userId: Long): Point {
        return pointRepository.findByUserId(userId) ?: Point.create(userId, BigDecimal.ZERO)
    }
    
    private fun saveChargeHistory(userId: Long, amount: BigDecimal) {
        val chargeType = pointHistoryTypeRepository.getChargeType()
        val history = PointHistory.charge(userId, amount, chargeType, "포인트 충전")
        pointHistoryRepository.save(history)
    }
    
    private fun saveDeductHistory(userId: Long, amount: BigDecimal, description: String) {
        val useType = pointHistoryTypeRepository.getUseType()
        val history = PointHistory.Companion.use(userId, amount, useType, description)
        pointHistoryRepository.save(history)
    }
}