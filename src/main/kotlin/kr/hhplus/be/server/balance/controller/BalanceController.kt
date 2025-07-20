package kr.hhplus.be.server.balance.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.balance.dto.BalanceResponse
import kr.hhplus.be.server.balance.dto.ChargeBalanceRequest
import kr.hhplus.be.server.balance.dto.PointHistoryResponse
import kr.hhplus.be.server.balance.service.BalanceService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/balance")
@Validated
class BalanceController(
    private val balanceService: BalanceService
) {
    
    @PostMapping("/charge")
    fun charge(@Valid @RequestBody request: ChargeBalanceRequest): ResponseEntity<BalanceResponse> {
        val point = balanceService.chargeBalance(request.userId, request.amount)
        return ResponseEntity.ok(BalanceResponse.from(point))
    }
    
    @GetMapping("/history/{userId}")
    fun history(
        @PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<List<PointHistoryResponse>> {
        val histories = balanceService.getPointHistory(userId)
        return ResponseEntity.ok(histories.map { PointHistoryResponse.from(it) })
    }
    
    @GetMapping("/{userId}")
    fun getBalance(
        @PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<BalanceResponse> {
        val point = balanceService.getBalance(userId)
        return ResponseEntity.ok(BalanceResponse.from(point))
    }
}
