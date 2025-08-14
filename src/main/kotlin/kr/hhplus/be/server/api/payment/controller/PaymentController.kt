package kr.hhplus.be.server.api.payment.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.global.response.CommonApiResponse
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.api.payment.usecase.ProcessPaymentUserCase
import kr.hhplus.be.server.domain.payment.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
@Validated
class PaymentController(
    private val processPaymentUserCase: ProcessPaymentUserCase,
    private val paymentService: PaymentService
) {


    @PostMapping
    fun processPayment(@Valid @RequestBody request: PaymentRequest): ResponseEntity<CommonApiResponse<PaymentDto>> {
        val payment = processPaymentUserCase.execute(request.userId, request.reservationId, request.seatId, request.token)
        return ResponseEntity.status(201).body(
            CommonApiResponse.Companion.success(
                data = payment,
                message = "결제가 완료되었습니다"
            )
        )
    }

    @GetMapping("/{paymentId}")
    fun getPayment(
        @PathVariable @Positive(message = "결제 ID는 양수여야 합니다") paymentId: Long
    ): ResponseEntity<CommonApiResponse<PaymentDto>> {
        val payment = paymentService.getPaymentById(paymentId)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = payment,
                message = "결제 정보 조회가 완료되었습니다"
            )
        )
    }
}