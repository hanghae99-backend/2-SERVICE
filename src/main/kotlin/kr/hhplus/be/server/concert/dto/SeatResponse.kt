package kr.hhplus.be.server.concert.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "좌석 정보 응답")
data class SeatResponse(
    @Schema(description = "좌석 ID", example = "1")
    val seatId: Long,
    
    @Schema(description = "좌석 번호", example = "1")
    val seatNumber: Int,
    
    @Schema(description = "좌석 가격", example = "100000")
    val price: BigDecimal,
    
    @Schema(description = "좌석 상태", example = "AVAILABLE")
    val status: String,
    
    @Schema(description = "임시 예약 만료 시간", example = "2024-01-01T15:05:00")
    val temporaryHoldExpiresAt: String?
)
