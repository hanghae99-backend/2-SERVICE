package kr.hhplus.be.server.domain.balance.rules

import kr.hhplus.be.server.global.exception.BusinessRuleViolationException
import java.math.BigDecimal

object BalanceBusinessRules {
    
    val MIN_CHARGE_AMOUNT = BigDecimal("1000")
    val MAX_CHARGE_AMOUNT = BigDecimal("1000000")
    val MAX_BALANCE_LIMIT = BigDecimal("50000000")
    val DAILY_CHARGE_LIMIT = BigDecimal("10000000")
    
    fun validateChargeAmount(amount: BigDecimal) {
        when {
            amount < MIN_CHARGE_AMOUNT -> throw BusinessRuleViolationException(
                "최소 충전 금액은 ${MIN_CHARGE_AMOUNT}원입니다. 요청 금액: $amount"
            )
            amount > MAX_CHARGE_AMOUNT -> throw BusinessRuleViolationException(
                "최대 충전 금액은 ${MAX_CHARGE_AMOUNT}원입니다. 요청 금액: $amount"
            )
        }
    }
    
    fun validateBalanceLimit(currentBalance: BigDecimal, chargeAmount: BigDecimal) {
        val afterChargeBalance = currentBalance.add(chargeAmount)
        if (afterChargeBalance > MAX_BALANCE_LIMIT) {
            throw BusinessRuleViolationException(
                "최대 잔액 한도를 초과합니다. 현재: $currentBalance, 충전 후: $afterChargeBalance, 한도: $MAX_BALANCE_LIMIT"
            )
        }
    }
    
    fun validateDailyChargeLimit(userId: Long, todayChargeAmount: BigDecimal, requestAmount: BigDecimal) {
        val afterChargeTotal = todayChargeAmount.add(requestAmount)
        if (afterChargeTotal > DAILY_CHARGE_LIMIT) {
            throw BusinessRuleViolationException(
                "일일 충전 한도를 초과합니다. 오늘 충전액: $todayChargeAmount, 요청 금액: $requestAmount, 한도: $DAILY_CHARGE_LIMIT"
            )
        }
    }
}