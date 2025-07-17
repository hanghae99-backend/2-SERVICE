package kr.hhplus.be.server.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "대기열 상태 조회 응답")
data class QueueStatusResponse(
    @Schema(description = "토큰", example = "token-uuid-1234")
    val token: String,
    
    @Schema(description = "토큰 상태", example = "WAITING")
    val status: String,
    
    @Schema(description = "상태 메시지", example = "대기 중입니다")
    val message: String,
    
    @Schema(description = "대기 순서", example = "5")
    val queuePosition: Int?,
    
    @Schema(description = "예상 대기 시간 (분)", example = "10")
    val estimatedWaitingTime: Int?
)
