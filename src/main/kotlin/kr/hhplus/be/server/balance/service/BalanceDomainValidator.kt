package kr.hhplus.be.server.balance.service

import kr.hhplus.be.server.balance.entity.Point
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 잔액 도메인 검증의 단일 책임을 가진다
 * 비즈니스 규칙과 도메인 상태에 대한 검증을 담당
 */
@Component
class BalanceDomainValidator {
    
    /**
     * 포인트 존재 여부 검증
     * 비즈니스 규칙: 차감할 포인트가 존재해야 함
     */
    fun validatePointExists(point: Point?) {
        if (point == null) {
            throw kr.hhplus.be.server.balance.entity.PointNotFoundException("포인트 정보를 찾을 수 없습니다")
        }
    }
    
    /**
     * 잔액 충분성 검증
     * 비즈니스 규칙: 차감할 금액만큼 잔액이 있어야 함
     */
    fun validateSufficientBalance(point: Point, amount: BigDecimal) {
        if (!point.hasEnoughBalance(amount)) {
            throw kr.hhplus.be.server.balance.entity.InsufficientBalanceException(
                "잔액이 부족합니다. 현재 잔액: ${point.amount}, 차감 요청: $amount"
            )
        }
    }
    
    /**
     * 최대 잔액 한도 검증
     * 비즈니스 규칙: 최대 잔액 한도를 초과할 수 없음
     */
    fun validateMaxBalanceLimit(currentAmount: BigDecimal, chargeAmount: BigDecimal) {
        val maxBalance = BigDecimal("50000000") // 5천만원 한도
        val newAmount = currentAmount.add(chargeAmount)
        
        if (newAmount > maxBalance) {
            throw kr.hhplus.be.server.balance.entity.InvalidPointAmountException(
                "최대 잔액 한도를 초과합니다. 현재: $currentAmount, 충전 후: $newAmount, 한도: $maxBalance"
            )
        }
    }
    
    /**
     * 최소 충전 금액 검증
     * 비즈니스 규칙: 최소 충전 금액이 있음
     */
    fun validateMinChargeAmount(amount: BigDecimal) {
        val minChargeAmount = BigDecimal("1000") // 최소 1000원
        
        if (amount < minChargeAmount) {
            throw kr.hhplus.be.server.balance.entity.InvalidPointAmountException(
                "최소 충전 금액은 ${minChargeAmount}원입니다: $amount"
            )
        }
    }
    
    /**
     * 포인트 상태 유효성 검증
     * 비즈니스 규칙: 포인트가 정상 상태여야 함
     */
    fun validatePointStatus(point: Point) {
        if (point.amount < BigDecimal.ZERO) {
            throw IllegalStateException("포인트 잔액이 음수입니다: ${point.amount}")
        }
    }
}
