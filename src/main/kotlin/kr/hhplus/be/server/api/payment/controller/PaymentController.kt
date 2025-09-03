package kr.hhplus.be.server.api.payment.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.global.response.CommonApiResponse
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.domain.payment.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/payments")
@Validated
@Tag(name = "Payment", description = "결제 관리 API")
class PaymentController(
    private val paymentService: PaymentService
) {

    @Operation(
        summary = "결제 처리",
        description = "콘서트 좌석 예약에 대한 결제를 처리합니다. 포인트 차감, 예약 확정, 좌석 확정이 원자적으로 처리됩니다."
    )
    @PostMapping
    fun processPayment(
        @Valid @RequestBody 
        @Parameter(description = "결제 요청", required = true)
        request: PaymentRequest
    ): ResponseEntity<CommonApiResponse<PaymentDto>> {
        // 결제 처리
        val payment = paymentService.processPayment(
            userId = request.userId,
            reservationId = request.reservationId,
            token = request.token,
            amount = request.amount
        )
        
        return ResponseEntity.status(201).body(
            CommonApiResponse.success(
                data = payment,
                message = "결제가 완료되었습니다"
            )
        )
    }

    @Operation(
        summary = "결제 정보 조회",
        description = "특정 결제의 상세 정보를 조회합니다."
    )
    @GetMapping("/{paymentId}")
    fun getPayment(
        @PathVariable 
        @Parameter(description = "결제 ID", required = true, example = "1")
        @Positive(message = "결제 ID는 양수여야 합니다") 
        paymentId: Long
    ): ResponseEntity<CommonApiResponse<PaymentDto>> {
        val payment = paymentService.getPaymentById(paymentId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = payment,
                message = "결제 정보 조회가 완료되었습니다"
            )
        )
    }
}
