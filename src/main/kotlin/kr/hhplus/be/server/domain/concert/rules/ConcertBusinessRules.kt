package kr.hhplus.be.server.domain.concert.rules

import kr.hhplus.be.server.global.exception.BusinessRuleViolationException
import kr.hhplus.be.server.global.properties.ConcertProperties
import java.time.LocalDateTime

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