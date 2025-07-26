package kr.hhplus.be.server.balance.service

import kr.hhplus.be.server.balance.entity.*
import kr.hhplus.be.server.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.balance.exception.PointNotFoundException
import kr.hhplus.be.server.balance.repository.PointHistoryRepository
import kr.hhplus.be.server.balance.repository.PointRepository
import kr.hhplus.be.server.balance.repository.PointHistoryTypePojoRepository
import kr.hhplus.be.server.user.exception.UserNotFoundException
import kr.hhplus.be.server.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class BalanceService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
    private val userService: UserService
) {
    
    @Transactional
    fun chargeBalance(userId: Long, amount: BigDecimal): Point {
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        val currentPoint = pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
        
        val maxBalance = BigDecimal("50000000")
        val newAmount = currentPoint.amount.add(amount)
        if (newAmount > maxBalance) {
            throw InvalidPointAmountException(
                "최대 잔액 한도를 초과합니다. 현재: ${currentPoint.amount}, 충전 후: $newAmount, 한도: $maxBalance"
            )
        }
        
        val minChargeAmount = BigDecimal("1000")
        if (amount < minChargeAmount) {
            throw InvalidPointAmountException(
                "최소 충전 금액은 ${minChargeAmount}원입니다: $amount"
            )
        }
        
        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)
        
        val chargeType = pointHistoryTypeRepository.getChargeType()
        val history = PointHistory.charge(userId, amount, chargeType, "포인트 충전")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }

    fun getBalance(userId: Long): Point {
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
    }

    @Transactional
    fun deductBalance(userId: Long, amount: BigDecimal): Point {
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        val currentPoint = pointRepository.findByUserId(userId)
            ?: throw PointNotFoundException("포인트 정보를 찾을 수 없습니다")
        
        val deductedPoint = currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(deductedPoint)
        
        val useType = pointHistoryTypeRepository.getUseType()
        val history = PointHistory.use(userId, amount, useType, "포인트 사용")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }

    fun getPointHistory(userId: Long): List<PointHistory> {
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
}
