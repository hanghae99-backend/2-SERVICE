package kr.hhplus.be.server.domain.common

import kr.hhplus.be.server.domain.common.BusinessRuleViolationException
import kr.hhplus.be.server.global.properties.ConcertProperties
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 비즈니스 규칙을 중앙화하여 관리하는 객체들
 */

class ReservationBusinessRules(
    private val concertProperties: ConcertProperties
) {
    
    fun validateReservationLimit(userId: Long, currentCount: Int) {
        if (currentCount >= concertProperties.maxReservationsPerUser) {
            throw BusinessRuleViolationException(
                "사용자당 최대 예약 가능 수를 초과했습니다. 현재: $currentCount, 최대: ${concertProperties.maxReservationsPerUser}"
            )
        }
    }
    
    fun calculateExpirationTime(): LocalDateTime {
        return LocalDateTime.now().plus(concertProperties.reservationTimeout)
    }
    
    fun validateConcurrentReservationLimit(userId: Long, activeReservationCount: Int) {
        if (activeReservationCount >= concertProperties.maxConcurrentReservations) {
            throw BusinessRuleViolationException(
                "동시 예약 가능 수를 초과했습니다. 현재: $activeReservationCount, 최대: ${concertProperties.maxConcurrentReservations}"
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

class ConcertBusinessRules(
    private val concertProperties: ConcertProperties
) {
    
    fun validateSeatCount(seatCount: Int) {
        if (seatCount > concertProperties.maxSeatsPerSchedule) {
            throw BusinessRuleViolationException(
                "스케줄당 최대 좌석 수를 초과합니다. 요청: $seatCount, 최대: ${concertProperties.maxSeatsPerSchedule}"
            )
        }
    }
    
    fun validateConcertDuration(startTime: LocalDateTime, endTime: LocalDateTime) {
        val durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes()
        when {
            durationMinutes < concertProperties.minConcertDurationMinutes -> throw BusinessRuleViolationException(
                "콘서트 최소 진행 시간은 ${concertProperties.minConcertDurationMinutes}분입니다. 현재: ${durationMinutes}분"
            )
            durationMinutes > concertProperties.maxConcertDurationMinutes -> throw BusinessRuleViolationException(
                "콘서트 최대 진행 시간은 ${concertProperties.maxConcertDurationMinutes}분입니다. 현재: ${durationMinutes}분"
            )
        }
    }
    
    fun validateScheduleTime(scheduleTime: LocalDateTime) {
        val now = LocalDateTime.now()
        if (scheduleTime.isBefore(now.plus(concertProperties.minScheduleAdvance))) {
            throw BusinessRuleViolationException(
                "콘서트 스케줄은 최소 ${concertProperties.minScheduleAdvanceHours}시간 후부터 등록 가능합니다. 요청 시간: $scheduleTime"
            )
        }
    }
}
