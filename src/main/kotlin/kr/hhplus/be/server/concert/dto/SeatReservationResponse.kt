package kr.hhplus.be.server.concert.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "좌석 예약 응답")
data class SeatReservationResponse(
    @Schema(description = "예약 ID", example = "1")
    val reservationId: Long,
    
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "좌석 정보")
    val seat: SeatResponse,
    
    @Schema(description = "예약 상태", example = "TEMPORARY")
    val status: String,
    
    @Schema(description = "예약 생성 시간", example = "2024-01-01T15:00:00")
    val createdAt: String,
    
    @Schema(description = "임시 예약 만료 시간", example = "2024-01-01T15:05:00")
    val expiresAt: String
)
