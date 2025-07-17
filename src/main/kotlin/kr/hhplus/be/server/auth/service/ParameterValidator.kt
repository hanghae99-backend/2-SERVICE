package kr.hhplus.be.server.auth.service

import org.springframework.stereotype.Component

/**
 * 파라미터 검증의 단일 책임을 가진다
 * 입력값 형식, 범위 등 기본적인 검증을 담당
 */
@Component
class ParameterValidator {
    
    /**
     * 사용자 ID 파라미터 검증
     */
    fun validateUserId(userId: Long) {
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다: $userId" }
    }
    
    /**
     * 토큰 파라미터 검증
     */
    fun validateToken(token: String) {
        require(token.isNotBlank()) { "토큰은 비어있을 수 없습니다" }
        require(token.length >= 10) { "토큰 길이가 너무 짧습니다" }
    }
    
    /**
     * 좌석 ID 파라미터 검증
     */
    fun validateSeatId(seatId: Long) {
        require(seatId > 0) { "좌석 ID는 0보다 커야 합니다: $seatId" }
    }
    
    /**
     * 예약 ID 파라미터 검증
     */
    fun validateReservationId(reservationId: Long) {
        require(reservationId > 0) { "예약 ID는 0보다 커야 합니다: $reservationId" }
    }
}
