package kr.hhplus.be.server.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "대기열 토큰 발급 요청")
data class TokenIssueRequest(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long
) {
    init {
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다" }
    }
}
