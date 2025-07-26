package kr.hhplus.be.server.user.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

data class UserCreateRequest(
    @field:Schema(
        description = "유저 ID",
        required = true,
        example = "1L"
    )
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    val userId: Long,
)