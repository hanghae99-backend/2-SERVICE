package kr.hhplus.be.server.internal.reservation.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.api.reservation.dto.request.ReservationConfirmRequest
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/reservations")
@Validated
@Tag(name = "Internal Reservation", description = "내부 예약 관리 API (이벤트용)")
class ReservationInternalController(
    private val reservationService: ReservationService
) {

    @Operation(
        summary = "예약 확정 (내부용)",
        description = "이벤트 시스템에서 예약을 확정합니다."
    )
    @PutMapping("/{reservationId}/confirm")
    fun confirmReservation(
        @PathVariable 
        @Parameter(description = "예약 ID", required = true, example = "1")
        @Positive(message = "예약 ID는 양수여야 합니다")
        reservationId: Long,
        
        @Valid @RequestBody 
        @Parameter(description = "예약 확정 요청", required = true)
        request: ReservationConfirmRequest
    ): ResponseEntity<CommonApiResponse<*>> {
        reservationService.confirmReservation(
            reservationId = reservationId,
            paymentId = request.paymentId
        )
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = "OK",
                message = "예약 확정 완료"
            )
        )
    }
}