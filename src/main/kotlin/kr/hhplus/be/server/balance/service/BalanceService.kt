package kr.hhplus.be.server.balance.service

import kr.hhplus.be.server.balance.entity.*
import kr.hhplus.be.server.balance.repository.PointHistoryRepository
import kr.hhplus.be.server.balance.repository.PointRepository
import kr.hhplus.be.server.user.entity.UserNotFoundException
import kr.hhplus.be.server.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class BalanceService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val userService: UserService
) {
    
    @Transactional
    fun chargeBalance(userId: Long, amount: BigDecimal): Point {
        if (amount <= BigDecimal.ZERO) {
            throw InvalidPointAmountException("충전 금액은 0보다 커야 합니다: $amount")
        }
        
        // 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 기존 포인트 조회 또는 새로 생성
        val currentPoint = pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
        
        // 포인트 충전
        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)
        
        // 충전 이력 저장
        val history = PointHistory.charge(userId, amount, "포인트 충전")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }
    
    fun getBalance(userId: Long): Point {
        // 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
    }
    
    @Transactional
    fun deductBalance(userId: Long, amount: BigDecimal): Point {
        if (amount <= BigDecimal.ZERO) {
            throw InvalidPointAmountException("차감 금액은 0보다 커야 합니다: $amount")
        }
        
        // 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        val currentPoint = pointRepository.findByUserId(userId)
            ?: throw PointNotFoundException("포인트 정보를 찾을 수 없습니다: $userId")
        
        // 포인트 차감
        val deductedPoint = currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(deductedPoint)
        
        // 사용 이력 저장
        val history = PointHistory.usage(userId, amount, "포인트 사용")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }
    
    fun checkBalance(userId: Long, amount: BigDecimal): Boolean {
        val point = pointRepository.findByUserId(userId)
            ?: return false
        
        return point.hasEnoughBalance(amount)
    }
    
    fun getPointHistory(userId: Long): List<PointHistory> {
        // 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
}
