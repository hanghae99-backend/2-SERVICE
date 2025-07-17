package kr.hhplus.be.server.payment.controller.mock

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.payment.dto.PaymentRequest
import kr.hhplus.be.server.payment.dto.PaymentResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/mock/payments")
@Tag(name = "결제 (Mock)", description = "결제 관리 Mock API - 개발/테스트용")
class MockPaymentController {
    
    @PostMapping
    @Operation(
        summary = "결제 처리",
        description = """
            **예약에 대한 결제를 처리합니다**
            
            ## 결제 프로세스
            1. 예약 정보 확인
            2. 사용자 잔액 확인
            3. 잔액 차감 및 결제 처리
            4. 예약 상태 확정
            
            ## 주의사항
            - 잔액 부족 시 결제 실패
            - 결제 완료 후 예약 취소 불가
            - 임시 예약의 만료 전에 결제 필요
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "결제 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PaymentResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "결제 실패 (잔액 부족, 예약 만료 등)",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "잔액 부족",
                            value = """{"error": "잔액이 부족합니다", "code": "INSUFFICIENT_BALANCE"}"""
                        )
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "예약을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "예약 없음",
                            value = """{"error": "예약을 찾을 수 없습니다", "code": "RESERVATION_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun processPayment(@RequestBody @Parameter(description = "결제 요청 정보") request: PaymentRequest): ResponseEntity<PaymentResponse> {
        val now = LocalDateTime.now()
        
        val response = PaymentResponse(
            paymentId = (1..1000).random().toLong(),
            userId = request.userId,
            reservationId = request.reservationId,
            amount = BigDecimal("100000"),
            status = "COMPLETED",
            paidAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            createdAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/history/{userId}")
    @Operation(
        summary = "결제 내역 조회",
        description = """
            **사용자의 결제 내역을 조회합니다**
            
            ## 결제 상태
            - `COMPLETED`: 결제 완료
            - `PENDING`: 결제 대기
            - `FAILED`: 결제 실패
            - `REFUNDED`: 환불 완료
            
            ## 정렬 순서
            - 최신 결제 순으로 정렬
            - 페이징 기능 추후 추가 예정
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = PaymentResponse::class))
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
    fun getPaymentHistory(@PathVariable @Parameter(description = "사용자 ID", example = "1") userId: Long): ResponseEntity<List<PaymentResponse>> {
        val payments = (1..5).map { index ->
            val createdAt = LocalDateTime.now().minusDays(index.toLong())
            PaymentResponse(
                paymentId = index.toLong(),
                userId = userId,
                reservationId = index.toLong(),
                amount = BigDecimal("100000"),
                status = if (index <= 3) "COMPLETED" else "PENDING",
                paidAt = if (index <= 3) createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) else null,
                createdAt = createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        }
        
        return ResponseEntity.ok(payments)
    }
    
    @GetMapping("/{paymentId}")
    @Operation(
        summary = "결제 상세 조회",
        description = """
            **특정 결제의 상세 정보를 조회합니다**
            
            ## 상세 정보
            - 결제 금액 및 상태
            - 결제 일시
            - 관련 예약 정보
            
            ## 활용 방법
            - 결제 영수증 역할
            - 결제 상태 확인
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PaymentResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "결제 정보를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "결제 없음",
                            value = """{"error": "결제 정보를 찾을 수 없습니다", "code": "PAYMENT_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun getPayment(@PathVariable @Parameter(description = "결제 ID", example = "1") paymentId: Long): ResponseEntity<PaymentResponse> {
        val now = LocalDateTime.now()
        
        val response = PaymentResponse(
            paymentId = paymentId,
            userId = 1L,
            reservationId = 1L,
            amount = BigDecimal("100000"),
            status = "COMPLETED",
            paidAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            createdAt = now.minusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        
        return ResponseEntity.ok(response)
    }
}
