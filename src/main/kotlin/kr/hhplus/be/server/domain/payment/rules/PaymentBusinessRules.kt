package kr.hhplus.be.server.domain.payment.rules

import kr.hhplus.be.server.global.exception.BusinessRuleViolationException
import java.math.BigDecimal

object PaymentBusinessRules {
    
    val MIN_PAYMENT_AMOUNT = BigDecimal("100")
    val MAX_PAYMENT_AMOUNT = BigDecimal("10000000")
    val DAILY_PAYMENT_LIMIT = BigDecimal("5000000")
    
    fun validatePaymentAmount(amount: BigDecimal) {
        when {
            amount < MIN_PAYMENT_AMOUNT -> throw BusinessRuleViolationException(
                "최소 결제 금액은 ${MIN_PAYMENT_AMOUNT}원입니다. 요청 금액: $amount"
            )
            amount > MAX_PAYMENT_AMOUNT -> throw BusinessRuleViolationException(
                "최대 결제 금액은 ${MAX_PAYMENT_AMOUNT}원입니다. 요청 금액: $amount"
            )
        }
    }
    
    fun validateDailyPaymentLimit(userId: Long, todayTotalAmount: BigDecimal, requestAmount: BigDecimal) {
        val afterPaymentTotal = todayTotalAmount.add(requestAmount)
        if (afterPaymentTotal > DAILY_PAYMENT_LIMIT) {
            throw BusinessRuleViolationException(
                "일일 결제 한도를 초과합니다. 오늘 결제액: $todayTotalAmount, 요청 금액: $requestAmount, 한도: $DAILY_PAYMENT_LIMIT"
            )
        }
    }
}