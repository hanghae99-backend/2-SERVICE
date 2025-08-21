package kr.hhplus.be.server.domain.common

import kr.hhplus.be.server.domain.common.BusinessRuleViolationException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 비즈니스 규칙을 중앙화하여 관리하는 객체들
 */

object ReservationBusinessRules {
    
    const val RESERVATION_TIMEOUT_MINUTES = 5L
    const val MAX_RESERVATIONS_PER_USER = 10
    const val MAX_CONCURRENT_RESERVATIONS = 3
    
    fun validateReservationLimit(userId: Long, currentCount: Int) {
        if (currentCount >= MAX_RESERVATIONS_PER_USER) {
            throw BusinessRuleViolationException(
                "사용자당 최대 예약 가능 수를 초과했습니다. 현재: $currentCount, 최대: $MAX_RESERVATIONS_PER_USER"
            )
        }
    }
    
    fun calculateExpirationTime(): LocalDateTime {
        return LocalDateTime.now().plusMinutes(RESERVATION_TIMEOUT_MINUTES)
    }
    
    fun validateConcurrentReservationLimit(userId: Long, activeReservationCount: Int) {
        if (activeReservationCount >= MAX_CONCURRENT_RESERVATIONS) {
            throw BusinessRuleViolationException(
                "동시 예약 가능 수를 초과했습니다. 현재: $activeReservationCount, 최대: $MAX_CONCURRENT_RESERVATIONS"
            )
        }
    }
}

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

object ConcertBusinessRules {
    
    const val MAX_SEATS_PER_SCHEDULE = 1000
    const val MIN_CONCERT_DURATION_MINUTES = 30
    const val MAX_CONCERT_DURATION_MINUTES = 480
    
    fun validateSeatCount(seatCount: Int) {
        if (seatCount > MAX_SEATS_PER_SCHEDULE) {
            throw BusinessRuleViolationException(
                "스케줄당 최대 좌석 수를 초과합니다. 요청: $seatCount, 최대: $MAX_SEATS_PER_SCHEDULE"
            )
        }
    }
    
    fun validateConcertDuration(startTime: LocalDateTime, endTime: LocalDateTime) {
        val durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes()
        when {
            durationMinutes < MIN_CONCERT_DURATION_MINUTES -> throw BusinessRuleViolationException(
                "콘서트 최소 진행 시간은 ${MIN_CONCERT_DURATION_MINUTES}분입니다. 현재: ${durationMinutes}분"
            )
            durationMinutes > MAX_CONCERT_DURATION_MINUTES -> throw BusinessRuleViolationException(
                "콘서트 최대 진행 시간은 ${MAX_CONCERT_DURATION_MINUTES}분입니다. 현재: ${durationMinutes}분"
            )
        }
    }
    
    fun validateScheduleTime(scheduleTime: LocalDateTime) {
        val now = LocalDateTime.now()
        if (scheduleTime.isBefore(now.plusHours(1))) {
            throw BusinessRuleViolationException(
                "콘서트 스케줄은 최소 1시간 후부터 등록 가능합니다. 요청 시간: $scheduleTime"
            )
        }
    }
}
