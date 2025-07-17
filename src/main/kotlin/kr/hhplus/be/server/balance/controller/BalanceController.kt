package kr.hhplus.be.server.balance.controller

import kr.hhplus.be.server.balance.dto.BalanceResponse
import kr.hhplus.be.server.balance.dto.ChargeBalanceRequest
import kr.hhplus.be.server.balance.dto.PointHistoryResponse
import kr.hhplus.be.server.balance.dto.UseBalanceRequest
import kr.hhplus.be.server.balance.service.BalanceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/balance")
class BalanceController(
    private val balanceService: BalanceService
) {
    
    @PostMapping("/charge")
    fun charge(@RequestBody request: ChargeBalanceRequest): ResponseEntity<BalanceResponse> {
        val point = balanceService.chargeBalance(request.userId, request.amount)
        return ResponseEntity.ok(BalanceResponse.from(point))
    }
    
    @PostMapping("/use")
    fun use(@RequestBody request: UseBalanceRequest): ResponseEntity<BalanceResponse> {
        val point = balanceService.deductBalance(request.userId, request.amount)
        return ResponseEntity.ok(BalanceResponse.from(point))
    }
    
    @GetMapping("/history/{userId}")
    fun history(@PathVariable userId: Long): ResponseEntity<List<PointHistoryResponse>> {
        val histories = balanceService.getPointHistory(userId)
        return ResponseEntity.ok(histories.map { PointHistoryResponse.from(it) })
    }
    
    @GetMapping("/{userId}")
    fun getBalance(@PathVariable userId: Long): ResponseEntity<BalanceResponse> {
        val point = balanceService.getBalance(userId)
        return ResponseEntity.ok(BalanceResponse.from(point))
    }
}
