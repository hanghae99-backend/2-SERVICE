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
        // 1. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 2. 기존 포인트 조회 또는 새로 생성
        val currentPoint = pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
        
        // 3. 최대 잔액 한도 검증
        val maxBalance = BigDecimal("50000000") // 5천만원 한도
        val newAmount = currentPoint.amount.add(amount)
        if (newAmount > maxBalance) {
            throw InvalidPointAmountException(
                "최대 잔액 한도를 초과합니다. 현재: ${currentPoint.amount}, 충전 후: $newAmount, 한도: $maxBalance"
            )
        }
        
        // 4. 최소 충전 금액 검증
        val minChargeAmount = BigDecimal("1000") // 최소 1000원
        if (amount < minChargeAmount) {
            throw InvalidPointAmountException(
                "최소 충전 금액은 ${minChargeAmount}원입니다: $amount"
            )
        }
        
        // 5. 포인트 충전 (Entity에서 비즈니스 로직 처리)
        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)
        
        // 6. 충전 이력 저장
        val history = PointHistory.charge(userId, amount, "포인트 충전")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }
    
    fun getBalance(userId: Long): Point {
        // 1. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
    }
    
    @Transactional
    fun deductBalance(userId: Long, amount: BigDecimal): Point {
        // 1. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 2. 포인트 조회
        val currentPoint = pointRepository.findByUserId(userId)
            ?: throw PointNotFoundException("포인트 정보를 찾을 수 없습니다")
        
        // 3. 포인트 차감 (Entity에서 잔액 충분성 검증 포함)
        val deductedPoint = currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(deductedPoint)
        
        // 4. 사용 이력 저장
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
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
}
