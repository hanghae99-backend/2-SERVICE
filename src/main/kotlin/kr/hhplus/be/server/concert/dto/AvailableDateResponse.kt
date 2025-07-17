package kr.hhplus.be.server.concert.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "예약 가능한 콘서트 날짜 응답")
data class AvailableDateResponse(
    @Schema(description = "콘서트 ID", example = "1")
    val concertId: Long,
    
    @Schema(description = "콘서트 제목", example = "아이유 콘서트")
    val title: String,
    
    @Schema(description = "아티스트", example = "아이유")
    val artist: String,
    
    @Schema(description = "콘서트 날짜", example = "2024-12-25")
    val concertDate: String,
    
    @Schema(description = "시작 시간", example = "19:00")
    val startTime: String,
    
    @Schema(description = "예약 가능 좌석 수", example = "45")
    val availableSeats: Int,
    
    @Schema(description = "전체 좌석 수", example = "50")
    val totalSeats: Int
)
