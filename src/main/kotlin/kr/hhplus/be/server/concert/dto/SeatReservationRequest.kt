package kr.hhplus.be.server.concert.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "좌석 예약 요청")
data class SeatReservationRequest(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "좌석 ID", example = "1")
    val seatId: Long,
    
    @Schema(description = "토큰", example = "token-uuid-1234")
    val token: String
) {
    init {
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다" }
        require(seatId > 0) { "좌석 ID는 0보다 커야 합니다" }
        require(token.isNotBlank()) { "토큰은 비어있을 수 없습니다" }
    }
}
