package kr.hhplus.be.server.api.balance.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.api.balance.dto.BalanceDto
import kr.hhplus.be.server.api.balance.dto.request.ChargeBalanceRequest
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/balance")
@Validated
@Tag(name = "Balance", description = "잔액 관리 API")
class BalanceController(
    private val balanceService: BalanceService
) {

    /**
     * 잔액 충전
     */
    @PostMapping
    @Operation(summary = "잔액 충전", description = "사용자의 포인트를 충전합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "충전 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    fun chargeBalance(
        @Valid @RequestBody request: ChargeBalanceRequest
    ): ResponseEntity<CommonApiResponse<BalanceDto.ChargeResult>> {
        val point = balanceService.chargeBalance(request.userId, request.amount)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = BalanceDto.ChargeResult.from(point, request.amount),
                message = "잔액 충전이 완료되었습니다"
            )
        )
    }

    /**
     * 잔액 조회
     */
    @GetMapping("/{userId}")
    @Operation(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    fun getBalance(
        @PathVariable
        @Parameter(description = "사용자 ID", required = true)
        @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<CommonApiResponse<BalanceDto.Detail>> {
        val point = balanceService.getBalance(userId)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = BalanceDto.Detail.from(point),
                message = "잔액 조회가 완료되었습니다"
            )
        )
    }

    /**
     * 포인트 이력 조회
     */
    @GetMapping("/history/{userId}")
    @Operation(summary = "포인트 이력 조회", description = "사용자의 포인트 충전/사용 이력을 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    fun getPointHistory(
        @PathVariable
        @Parameter(description = "사용자 ID", required = true)
        @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<CommonApiResponse<List<BalanceDto.History>>> {
        val histories = balanceService.getPointHistory(userId)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = histories.map { BalanceDto.History.from(it) },
                message = "포인트 이력 조회가 완료되었습니다"
            )
        )
    }
}