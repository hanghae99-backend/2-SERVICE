package kr.hhplus.be.server.balance.service

import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 잔액 파라미터 검증의 단일 책임을 가진다
 * 입력값 형식, 범위 등 기본적인 검증을 담당
 */
@Component
class BalanceParameterValidator {
    
    /**
     * 사용자 ID 파라미터 검증
     */
    fun validateUserId(userId: Long) {
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다: $userId" }
    }
    
    /**
     * 충전/차감 금액 파라미터 검증
     */
    fun validateAmount(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "금액은 0보다 커야 합니다: $amount" }
        require(amount <= BigDecimal("10000000")) { "금액이 너무 큽니다: $amount" }
        require(amount.scale() <= 2) { "소수점 둘째 자리까지만 허용됩니다: $amount" }
    }
    
    /**
     * 포인트 ID 파라미터 검증
     */
    fun validatePointId(pointId: Long) {
        require(pointId > 0) { "포인트 ID는 0보다 커야 합니다: $pointId" }
    }
    
    /**
     * 이력 ID 파라미터 검증
     */
    fun validateHistoryId(historyId: Long) {
        require(historyId > 0) { "이력 ID는 0보다 커야 합니다: $historyId" }
    }
}
