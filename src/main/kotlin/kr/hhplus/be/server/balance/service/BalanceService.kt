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
    private val userService: UserService,
    private val parameterValidator: BalanceParameterValidator,
    private val domainValidator: BalanceDomainValidator
) {
    
    @Transactional
    fun chargeBalance(userId: Long, amount: BigDecimal): Point {
        // 1. 파라미터 검증
        parameterValidator.validateUserId(userId)
        parameterValidator.validateAmount(amount)
        
        // 2. 비즈니스 규칙 검증
        domainValidator.validateMinChargeAmount(amount)
        
        // 3. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 4. 기존 포인트 조회 또는 새로 생성
        val currentPoint = pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
        
        // 5. 도메인 상태 검증
        domainValidator.validatePointStatus(currentPoint)
        
        // 6. 최대 잔액 한도 검증
        domainValidator.validateMaxBalanceLimit(currentPoint.amount, amount)
        
        // 7. 포인트 충전
        val chargedPoint = currentPoint.charge(amount)
        val savedPoint = pointRepository.save(chargedPoint)
        
        // 8. 충전 이력 저장
        val history = PointHistory.charge(userId, amount, "포인트 충전")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }
    
    fun getBalance(userId: Long): Point {
        // 1. 파라미터 검증
        parameterValidator.validateUserId(userId)
        
        // 2. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
    }
    
    @Transactional
    fun deductBalance(userId: Long, amount: BigDecimal): Point {
        // 1. 파라미터 검증
        parameterValidator.validateUserId(userId)
        parameterValidator.validateAmount(amount)
        
        // 2. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        // 3. 포인트 조회
        val currentPoint = pointRepository.findByUserId(userId)
        domainValidator.validatePointExists(currentPoint)
        
        // 4. 도메인 상태 검증
        domainValidator.validatePointStatus(currentPoint!!)
        
        // 5. 잔액 충분성 검증
        domainValidator.validateSufficientBalance(currentPoint, amount)
        
        // 6. 포인트 차감
        val deductedPoint = currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(deductedPoint)
        
        // 7. 사용 이력 저장
        val history = PointHistory.usage(userId, amount, "포인트 사용")
        pointHistoryRepository.save(history)
        
        return savedPoint
    }
    
    fun checkBalance(userId: Long, amount: BigDecimal): Boolean {
        // 1. 파라미터 검증
        parameterValidator.validateUserId(userId)
        parameterValidator.validateAmount(amount)
        
        val point = pointRepository.findByUserId(userId)
            ?: return false
        
        return point.hasEnoughBalance(amount)
    }
    
    fun getPointHistory(userId: Long): List<PointHistory> {
        // 1. 파라미터 검증
        parameterValidator.validateUserId(userId)
        
        // 2. 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
}
