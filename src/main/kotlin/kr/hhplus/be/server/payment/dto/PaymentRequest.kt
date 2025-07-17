package kr.hhplus.be.server.payment.dto

data class PaymentRequest(
    val userId: Long,
    val reservationId: Long,
    val token: String
) {
    init {
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다" }
        require(reservationId > 0) { "예약 ID는 0보다 커야 합니다" }
        require(token.isNotBlank()) { "토큰은 비어있을 수 없습니다" }
    }
}
