package kr.hhplus.be.server.api.balance.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

@Schema(description = "잔액 차감 요청")
data class DeductBalanceRequest(
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    @Schema(description = "사용자 ID", example = "1", required = true)
    val userId: Long,

    @field:NotNull(message = "차감 금액은 필수입니다")
    @field:DecimalMin(value = "1.0", message = "최소 차감 금액은 1원입니다")
    @field:DecimalMax(value = "10000000.0", message = "최대 차감 금액은 10,000,000원입니다")
    @Schema(description = "차감할 금액 (KRW)", example = "50000", required = true)
    val amount: BigDecimal,

    @Schema(description = "차감 사유", example = "콘서트 티켓 결제")
    val description: String = "포인트 사용"
)