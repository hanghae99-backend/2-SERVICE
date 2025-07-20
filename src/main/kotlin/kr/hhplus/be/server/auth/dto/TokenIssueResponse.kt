package kr.hhplus.be.server.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "대기열 토큰 발급 응답")
data class TokenIssueResponse(
    @Schema(description = "발급된 토큰", example = "token-uuid-1234")
    val token: String,
    
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "토큰 상태", example = "WAITING")
    val status: String,
    
    @Schema(description = "대기 순서", example = "5")
    val queuePosition: Int,
    
    @Schema(description = "토큰 발급 시간", example = "2024-01-01T00:00:00")
    val issuedAt: String,
    
    @Schema(description = "예상 대기 시간 (분)", example = "10")
    val estimatedWaitingTime: Int
)
