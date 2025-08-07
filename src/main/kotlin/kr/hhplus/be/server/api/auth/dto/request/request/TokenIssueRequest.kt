<<<<<<<< HEAD:src/main/kotlin/kr/hhplus/be/server/api/auth/dto/request/request/TokenIssueRequest.kt
package kr.hhplus.be.server.auth.dto.request
========
package kr.hhplus.be.server.api.auth.dto.request
>>>>>>>> step7:src/main/kotlin/kr/hhplus/be/server/api/auth/dto/request/TokenIssueRequest.kt

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

@Schema(description = "대기열 토큰 발급 요청")
data class TokenIssueRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    @Positive(message = "사용자 ID는 양수여야 합니다")
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long
)
