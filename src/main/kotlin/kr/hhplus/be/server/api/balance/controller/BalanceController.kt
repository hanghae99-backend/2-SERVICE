package kr.hhplus.be.server.api.balance.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.api.balance.dto.BalanceDto
import kr.hhplus.be.server.api.balance.dto.request.ChargeBalanceRequest
import kr.hhplus.be.server.api.balance.dto.request.DeductBalanceRequest
import kr.hhplus.be.server.api.balance.usecase.ChargeBalanceUseCase
import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/balance")
@Validated
@Tag(name = "Balance", description = "잔액 관리 API")
class BalanceController(
    private val balanceService: BalanceService,
    private val chargeBalanceUseCase: ChargeBalanceUseCase,
    private val deductBalanceUseCase: DeductBalanceUseCase
) {

    @Operation(
        summary = "잔액 충전",
        description = "사용자의 포인트 잔액을 충전합니다."
    )
    @PostMapping("/charge")
    fun chargeBalance(
        @Valid @RequestBody 
        @Parameter(description = "잔액 충전 요청", required = true)
        request: ChargeBalanceRequest
    ): ResponseEntity<CommonApiResponse<BalanceDto.ChargeResult>> {
        val point = chargeBalanceUseCase.execute(request.userId, request.amount)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = BalanceDto.ChargeResult.from(point, request.amount),
                message = "잔액 충전이 완료되었습니다"
            )
        )
    }

    @Operation(
        summary = "잔액 차감",
        description = "사용자의 포인트 잔액을 차감합니다."
    )
    @PostMapping("/deduct")
    fun deductBalance(
        @Valid @RequestBody 
        @Parameter(description = "잔액 차감 요청", required = true)
        request: DeductBalanceRequest
    ): ResponseEntity<CommonApiResponse<BalanceDto.Detail>> {
        val point = deductBalanceUseCase.execute(request.userId, request.amount, request.description)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = BalanceDto.Detail.from(point),
                message = "잔액 차감이 완료되었습니다"
            )
        )
    }

    @Operation(
        summary = "잔액 조회",
        description = "사용자의 현재 포인트 잔액을 조회합니다."
    )
    @GetMapping("/{userId}")
    fun getBalance(
        @PathVariable
        @Parameter(description = "사용자 ID", required = true, example = "1")
        @Positive(message = "사용자 ID는 양수여야 합니다") 
        userId: Long
    ): ResponseEntity<CommonApiResponse<BalanceDto.Detail>> {
        val point = balanceService.getBalance(userId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = BalanceDto.Detail.from(point),
                message = "잔액 조회가 완료되었습니다"
            )
        )
    }

    @Operation(
        summary = "포인트 이력 조회",
        description = "사용자의 포인트 충전/사용 이력을 조회합니다."
    )
    @GetMapping("/history/{userId}")
    fun getPointHistory(
        @PathVariable
        @Parameter(description = "사용자 ID", required = true, example = "1")
        @Positive(message = "사용자 ID는 양수여야 합니다") 
        userId: Long
    ): ResponseEntity<CommonApiResponse<List<BalanceDto.History>>> {
        val histories = balanceService.getPointHistory(userId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = histories.map { BalanceDto.History.from(it) },
                message = "포인트 이력 조회가 완료되었습니다"
            )
        )
    }
}
