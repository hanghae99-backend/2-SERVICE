package kr.hhplus.be.server.payment.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.payment.dto.PaymentRequest
import kr.hhplus.be.server.payment.dto.PaymentResponse
import kr.hhplus.be.server.payment.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payments")
@Validated
class PaymentController(
    private val paymentService: PaymentService
) {
    
    @PostMapping
    fun processPayment(@Valid @RequestBody request: PaymentRequest): ResponseEntity<PaymentResponse> {
        val payment = paymentService.processPayment(request.userId, request.reservationId, request.token)
        return ResponseEntity.ok(PaymentResponse.from(payment))
    }
    
    @GetMapping("/history/{userId}")
    fun getPaymentHistory(
        @PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<List<PaymentResponse>> {
        val payments = paymentService.getPaymentHistory(userId)
        return ResponseEntity.ok(payments.map { PaymentResponse.from(it) })
    }
    
    @GetMapping("/{paymentId}")
    fun getPayment(
        @PathVariable @Positive(message = "결제 ID는 양수여야 합니다") paymentId: Long
    ): ResponseEntity<PaymentResponse> {
        val payment = paymentService.getPaymentById(paymentId)
        return ResponseEntity.ok(PaymentResponse.from(payment))
    }
}
