package kr.hhplus.be.server.api.reservation.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.global.response.CommonApiResponse
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.api.reservation.usecase.ReserveSeatUseCase
import kr.hhplus.be.server.api.reservation.usecase.CancelReservationUseCase
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/reservations")
@Validated
class ReservationController(
    private val reservationService: ReservationService,
    private val reserveSeatUseCase: ReserveSeatUseCase,
    private val cancelReservationUseCase: CancelReservationUseCase
) {

    @PostMapping
    fun createReservation(
        @Valid @RequestBody request: ReservationCreateRequest
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = reserveSeatUseCase.execute(
            request.userId,
            request.concertId,
            request.seatId,
            request.token
        )
        return ResponseEntity.status(201).body(
            CommonApiResponse.success(
                data = ReservationDto.fromEntity(reservation),
                message = "좌석 예약 완료"
            )
        )
    }

    @DeleteMapping("/{reservationId}")
    fun cancelReservation(
        @PathVariable @Positive reservationId: Long,
        @Valid @RequestBody request: ReservationCancelRequest
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = cancelReservationUseCase.execute(
            reservationId,
            request.userId,
            request.cancelReason,
            request.token
        )
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = ReservationDto.fromEntity(reservation),
                message = "예약 취소 완료"
            )
        )
    }

    @GetMapping("/{reservationId}")
    fun getReservation(
        @PathVariable @Positive reservationId: Long
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
