package kr.hhplus.be.server.internal.balance.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.hhplus.be.server.internal.balance.dto.request.DeductBalanceRequest
import kr.hhplus.be.server.internal.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/balance")
@Validated
@Tag(name = "Internal Balance", description = "내부 잔액 관리 API (이벤트용)")
class BalanceInternalController(
    private val deductBalanceUseCase: DeductBalanceUseCase
) {

    @Operation(
        summary = "잔액 차감 (내부용)",
        description = "이벤트 시스템에서 잔액을 차감합니다."
    )
    @PostMapping("/deduct")
    fun deductBalance(
        @Valid @RequestBody 
        @Parameter(description = "잔액 차감 요청", required = true)
        request: DeductBalanceRequest
    ): ResponseEntity<CommonApiResponse<*>> {
        deductBalanceUseCase.execute(request.userId, request.amount, "이벤트 처리")
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = "OK",
                message = "잔액 차감 완료"
            )
        )
    }
}