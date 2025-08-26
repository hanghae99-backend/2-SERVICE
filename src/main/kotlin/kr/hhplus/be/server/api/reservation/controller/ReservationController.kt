package kr.hhplus.be.server.api.reservation.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.global.response.CommonApiResponse
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationConfirmRequest
import kr.hhplus.be.server.api.reservation.usecase.ReserveSeatUseCase
import kr.hhplus.be.server.api.reservation.usecase.CancelReservationUseCase
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/reservations")
@Validated
@Tag(name = "Reservation", description = "예약 관리 API")
class ReservationController(
    private val reservationService: ReservationService,
    private val reserveSeatUseCase: ReserveSeatUseCase,
    private val cancelReservationUseCase: CancelReservationUseCase
) {

    @Operation(
        summary = "좌석 예약 생성",
        description = "콘서트 좌석을 임시 예약합니다. 임시 예약은 5분간 유지되며, 결제를 완료해야 예약이 확정됩니다."
    )
    @PostMapping
    fun createReservation(
        @Valid @RequestBody 
        @Parameter(description = "예약 생성 요청", required = true)
        request: ReservationCreateRequest
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = reserveSeatUseCase.execute(
            userId = request.userId,
            concertId = request.concertId,
            seatId = request.seatId,
            token = request.token
        )
        
        return ResponseEntity.status(201).body(
            CommonApiResponse.success(
                data = ReservationDto.fromEntity(reservation),
                message = "좌석 예약 완료"
            )
        )
    }

    @Operation(
        summary = "예약 확정",
        description = "임시 예약을 확정 상태로 변경합니다. 결제 완료 후 호출됩니다."
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
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        reservationService.confirmReservation(reservationId, request.paymentId)
        val reservation = reservationService.getReservationById(reservationId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = ReservationDto.fromEntity(reservation),
                message = "예약 확정 완료"
            )
        )
    }

    @Operation(
        summary = "예약 취소",
        description = "기존 예약을 취소합니다. 결제 완료된 예약의 경우 환불 처리가 함께 진행됩니다."
    )
    @DeleteMapping("/{reservationId}")
    fun cancelReservation(
        @PathVariable 
        @Parameter(description = "예약 ID", required = true, example = "1")
        @Positive(message = "예약 ID는 양수여야 합니다")
        reservationId: Long,
        
        @Valid @RequestBody 
        @Parameter(description = "예약 취소 요청", required = true)
        request: ReservationCancelRequest
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = cancelReservationUseCase.execute(
            reservationId = reservationId,
            userId = request.userId,
            cancelReason = request.cancelReason,
            token = request.token
        )
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = ReservationDto.fromEntity(reservation),
                message = "예약 취소 완료"
            )
        )
    }

    @Operation(
        summary = "예약 정보 조회",
        description = "특정 예약의 상세 정보를 조회합니다."
    )
    @GetMapping("/{reservationId}")
    fun getReservation(
        @PathVariable 
        @Parameter(description = "예약 ID", required = true, example = "1")
        @Positive(message = "예약 ID는 양수여야 합니다")
        reservationId: Long
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = reservationService.getReservationById(reservationId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = ReservationDto.fromEntity(reservation),
                message = "예약 정보 조회 완료"
            )
        )
    }
}
