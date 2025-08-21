package kr.hhplus.be.server.domain.common

/**
 * Value Objects for improved state management
 */

/**
 * 예약 상태를 관리하는 Value Object
 */
data class ReservationStatus(
    val code: String,
    val name: String,
    val description: String
) {
    companion object {
        val TEMPORARY = ReservationStatus("TEMP", "임시예약", "5분간 유효한 임시 예약")
        val CONFIRMED = ReservationStatus("CONF", "확정", "결제 완료된 확정 예약")
        val CANCELLED = ReservationStatus("CANC", "취소", "취소된 예약")
        val EXPIRED = ReservationStatus("EXPR", "만료", "시간 만료로 인한 자동 취소")
    }
    
    /**
     * 상태 전환 가능 여부 검증
     */
    fun canTransitionTo(target: ReservationStatus): Boolean {
        return when (this) {
            TEMPORARY -> target in listOf(CONFIRMED, CANCELLED, EXPIRED)
            CONFIRMED -> target == CANCELLED
            CANCELLED -> false
            EXPIRED -> false
            else -> false
        }
    }
    
    /**
     * 활성 상태 여부 (예약이 유효한 상태)
     */
    fun isActive(): Boolean = this in listOf(TEMPORARY, CONFIRMED)
    
    /**
     * 완료 상태 여부 (더 이상 변경할 수 없는 상태)
     */
    fun isFinal(): Boolean = this in listOf(CANCELLED, EXPIRED)
}

/**
 * 결제 상태를 관리하는 Value Object
 */
data class PaymentStatus(
    val code: String,
    val name: String,
    val description: String
) {
    companion object {
        val PENDING = PaymentStatus("PEND", "대기", "결제 처리 중")
        val COMPLETED = PaymentStatus("COMP", "완료", "결제 성공")
        val FAILED = PaymentStatus("FAIL", "실패", "결제 실패")
        val CANCELLED = PaymentStatus("CANC", "취소", "결제 취소")
        val REFUNDED = PaymentStatus("REFD", "환불", "결제 환불 완료")
    }
    
    fun canTransitionTo(target: PaymentStatus): Boolean {
        return when (this) {
            PENDING -> target in listOf(COMPLETED, FAILED, CANCELLED)
            COMPLETED -> target in listOf(CANCELLED, REFUNDED)
            FAILED -> target == CANCELLED
            CANCELLED -> false
            REFUNDED -> false
            else -> false
        }
    }
    
    fun isProcessable(): Boolean = this == PENDING
    fun isSuccessful(): Boolean = this == COMPLETED
    fun isFinal(): Boolean = this in listOf(FAILED, CANCELLED, REFUNDED)
}

/**
 * 좌석 상태를 관리하는 Value Object
 */
data class SeatStatus(
    val code: String,
    val name: String,
    val description: String
) {
    companion object {
        val AVAILABLE = SeatStatus("AVAIL", "예약가능", "예약 가능한 좌석")
        val RESERVED = SeatStatus("RESV", "임시예약", "임시 예약된 좌석")
        val OCCUPIED = SeatStatus("OCCU", "예약완료", "예약이 확정된 좌석")
        val MAINTENANCE = SeatStatus("MAINT", "정비중", "정비로 인한 사용 불가")
        val BLOCKED = SeatStatus("BLOCK", "차단", "관리자에 의한 차단")
    }
    
    fun canTransitionTo(target: SeatStatus): Boolean {
        return when (this) {
            AVAILABLE -> target in listOf(RESERVED, MAINTENANCE, BLOCKED)
            RESERVED -> target in listOf(OCCUPIED, AVAILABLE, MAINTENANCE)
            OCCUPIED -> target in listOf(AVAILABLE, MAINTENANCE) // 공연 종료 후 해제
            MAINTENANCE -> target in listOf(AVAILABLE, BLOCKED)
            BLOCKED -> target in listOf(AVAILABLE, MAINTENANCE)
            else -> false
        }
    }
    
    fun isBookable(): Boolean = this == AVAILABLE
    fun isTemporary(): Boolean = this == RESERVED
    fun isUnavailable(): Boolean = this in listOf(OCCUPIED, MAINTENANCE, BLOCKED)
}

/**
 * 토큰 상태를 관리하는 Value Object
 */
data class TokenStatus(
    val code: String,
    val name: String,
    val description: String
) {
    companion object {
        val WAITING = TokenStatus("WAIT", "대기중", "대기열에서 대기 중")
        val ACTIVE = TokenStatus("ACTV", "활성", "서비스 이용 가능")
        val EXPIRED = TokenStatus("EXPR", "만료", "시간 만료로 인한 토큰 무효화")
        val USED = TokenStatus("USED", "사용완료", "서비스 이용 완료")
    }
    
    fun canTransitionTo(target: TokenStatus): Boolean {
        return when (this) {
            WAITING -> target in listOf(ACTIVE, EXPIRED)
            ACTIVE -> target in listOf(USED, EXPIRED)
            EXPIRED -> false
            USED -> false
            else -> false
        }
    }
    
    fun isUsable(): Boolean = this == ACTIVE
    fun isWaiting(): Boolean = this == WAITING
    fun isValid(): Boolean = this in listOf(WAITING, ACTIVE)
}
