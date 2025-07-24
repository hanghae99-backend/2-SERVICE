package kr.hhplus.be.server.payment.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.payment.dto.PaymentDto
import kr.hhplus.be.server.payment.dto.PaymentRequest
import kr.hhplus.be.server.payment.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/payments")
@Validated
class PaymentController(
    private val paymentService: PaymentService
) {
    
    /**
     * 결제 처리 (예약 기반)
     */
    @PostMapping
    fun processPayment(@Valid @RequestBody request: PaymentRequest): ResponseEntity<PaymentDto> {
        val payment = paymentService.processPayment(request.userId, request.reservationId, request.token)
        return ResponseEntity.status(201).body(payment)
    }
    
    /**
     * 특정 결제 정보 조회
     */
    @GetMapping("/{paymentId}")
    fun getPayment(
        @PathVariable @Positive(message = "결제 ID는 양수여야 합니다") paymentId: Long
    ): ResponseEntity<PaymentDto> {
        val payment = paymentService.getPaymentById(paymentId)
        return ResponseEntity.ok(payment)
    }
}
