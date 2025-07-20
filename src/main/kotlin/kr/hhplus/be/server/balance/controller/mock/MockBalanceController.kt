package kr.hhplus.be.server.balance.controller.mock

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.balance.dto.BalanceResponse
import kr.hhplus.be.server.balance.dto.ChargeBalanceRequest
import kr.hhplus.be.server.balance.dto.PointHistoryResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/mock/balance")
@Tag(name = "포인트 잔액 (Mock)", description = "포인트 잔액 관리 Mock API - 개발/테스트용")
class MockBalanceController {
    
    @PostMapping("/charge")
    @Operation(
        summary = "포인트 충전",
        description = """
            **사용자 포인트를 충전합니다**
            
            ## 충전 규칙
            - 최소 충전 금액: 1,000원
            - 최대 충전 금액: 1,000,000원
            - 충전 후 즉시 사용 가능
            
            ## 주의사항
            - 충전 후 포인트는 환불되지 않습니다
            - 충전 내역은 포인트 이력에서 확인 가능합니다
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "충전 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = BalanceResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 충전 요청",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "금액 오류",
                            value = """{"error": "충전 금액은 1,000원 이상이어야 합니다", "code": "INVALID_AMOUNT"}"""
                        )
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "사용자 없음",
                            value = """{"error": "사용자를 찾을 수 없습니다", "code": "USER_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun charge(@RequestBody @Parameter(description = "포인트 충전 요청 정보") request: ChargeBalanceRequest): ResponseEntity<BalanceResponse> {
        val currentBalance = BigDecimal("150000")
        val newBalance = currentBalance.add(request.amount)
        
        val response = BalanceResponse(
            userId = request.userId,
            balance = newBalance,
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/{userId}")
    @Operation(
        summary = "잔액 조회",
        description = """
            **사용자의 현재 잔액을 조회합니다**
            
            ## 응답 정보
            - 현재 포인트 잔액
            - 마지막 업데이트 시간
            
            ## 활용 방법
            - 결제 전 잔액 확인
            - 충전 필요 여부 판단
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = BalanceResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "사용자 없음",
                            value = """{"error": "사용자를 찾을 수 없습니다", "code": "USER_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun getBalance(@PathVariable @Parameter(description = "조회할 사용자 ID", example = "1") userId: Long): ResponseEntity<BalanceResponse> {
        val response = BalanceResponse(
            userId = userId,
            balance = BigDecimal("250000"),
            lastUpdated = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/history/{userId}")
    @Operation(
        summary = "포인트 이력 조회",
        description = """
            **사용자의 포인트 사용 이력을 조회합니다**
            
            ## 이력 유형
            - `CHARGE`: 포인트 충전
            - `USAGE`: 포인트 사용 (결제)
            - `REFUND`: 포인트 환불
            
            ## 정렬 순서
            - 최신 순으로 정렬됩니다
            - 페이징은 추후 지원 예정
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = PointHistoryResponse::class))
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "사용자 없음",
                            value = """{"error": "사용자를 찾을 수 없습니다", "code": "USER_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun history(@PathVariable @Parameter(description = "조회할 사용자 ID", example = "1") userId: Long): ResponseEntity<List<PointHistoryResponse>> {
        val histories = listOf(
            PointHistoryResponse(
                historyId = 3L,
                userId = userId,
                amount = BigDecimal("100000"),
                type = "CHARGE",
                description = "포인트 충전",
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ),
            PointHistoryResponse(
                historyId = 2L,
                userId = userId,
                amount = BigDecimal("50000"),
                type = "USAGE",
                description = "콘서트 티켓 결제",
                createdAt = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ),
            PointHistoryResponse(
                historyId = 1L,
                userId = userId,
                amount = BigDecimal("200000"),
                type = "CHARGE",
                description = "포인트 충전",
                createdAt = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )
        
        return ResponseEntity.ok(histories)
    }
}
