package kr.hhplus.be.server.api.user.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

@Schema(description = "사용자 생성 요청")
data class UserCreateRequest(
    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(min = 2, max = 50, message = "이름은 2-50자 사이여야 합니다")
    @Schema(description = "사용자 이름", example = "홍길동", required = true)
    val name: String,

    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    @field:Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
    @Schema(description = "이메일 주소", example = "hong@example.com", required = true)
    val email: String,

    @field:NotBlank(message = "전화번호는 필수입니다")
    @field:Pattern(
        regexp = "^\\d{3}-\\d{3,4}-\\d{4}$",
        message = "전화번호 형식이 올바르지 않습니다 (예: 010-1234-5678)"
    )
    @Schema(description = "전화번호", example = "010-1234-5678", required = true)
    val phoneNumber: String
) {
    init {
        require(name.isNotBlank()) { "이름은 공백일 수 없습니다" }
        require(email.isNotBlank()) { "이메일은 공백일 수 없습니다" }
        require(phoneNumber.isNotBlank()) { "전화번호는 공백일 수 없습니다" }
        require(phoneNumber.matches(Regex("^\\d{3}-\\d{3,4}-\\d{4}$"))) { 
            "전화번호 형식이 올바르지 않습니다 (예: 010-1234-5678)" 
        }
    }
}
