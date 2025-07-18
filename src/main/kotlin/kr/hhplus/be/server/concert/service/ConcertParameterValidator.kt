package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.concert.entity.InvalidConcertDateException
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 콘서트 파라미터 검증의 단일 책임을 가진다
 * 입력값 형식, 범위 등 기본적인 검증을 담당
 */
@Component
class ConcertParameterValidator {
    
    /**
     * 사용자 ID 파라미터 검증
     */
    fun validateUserId(userId: Long) {
        if (userId <= 0) {
            throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
        }
    }
    
    /**
     * 콘서트 ID 파라미터 검증
     */
    fun validateConcertId(concertId: Long) {
        if (concertId <= 0) {
            throw ParameterValidationException("콘서트 ID는 0보다 커야 합니다: $concertId")
        }
    }
    
    /**
     * 좌석 ID 파라미터 검증
     */
    fun validateSeatId(seatId: Long) {
        if (seatId <= 0) {
            throw ParameterValidationException("좌석 ID는 0보다 커야 합니다: $seatId")
        }
    }
    
    /**
     * 콘서트 날짜 파라미터 검증
     */
    fun validateConcertDate(concertDate: LocalDate) {
        try {
            val today = LocalDate.now()
            if (concertDate.isBefore(today)) {
                throw InvalidConcertDateException("콘서트 날짜는 오늘 이후여야 합니다: $concertDate")
            }
        } catch (e: Exception) {
            when (e) {
                is InvalidConcertDateException -> throw e
                else -> throw ParameterValidationException("콘서트 날짜 검증 중 오류가 발생했습니다", e)
            }
        }
    }
}
