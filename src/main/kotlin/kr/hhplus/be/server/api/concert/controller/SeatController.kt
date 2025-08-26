package kr.hhplus.be.server.api.concert.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/seats")
@Validated
@Tag(name = "Seat", description = "좌석 관리 API")
class SeatController(
    private val seatService: SeatService
) {

    @Operation(
        summary = "좌석 정보 조회",
        description = "특정 좌석의 상세 정보를 조회합니다."
    )
    @GetMapping("/{seatId}")
    fun getSeatInfo(
        @PathVariable 
        @Parameter(description = "좌석 ID", required = true, example = "1")
        @Positive(message = "좌석 ID는 양수여야 합니다")
        seatId: Long
    ): ResponseEntity<CommonApiResponse<SeatDto>> {
        val seat = seatService.getSeatById(seatId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = seat,
                message = "좌석 정보 조회 완료"
            )
        )
    }

    @Operation(
        summary = "좌석 가용성 검증",
        description = "좌석이 예약 가능한지 검증합니다."
    )
    @GetMapping("/{seatId}/validate")
    fun validateSeatAvailability(
        @PathVariable 
        @Parameter(description = "좌석 ID", required = true, example = "1")
        @Positive(message = "좌석 ID는 양수여야 합니다")
        seatId: Long
    ): ResponseEntity<CommonApiResponse<Unit>> {
        seatService.validateSeatAvailability(seatId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = Unit,
                message = "좌석 예약 가능"
            )
        )
    }

    @Operation(
        summary = "좌석 예약 처리",
        description = "좌석 상태를 예약됨으로 변경합니다."
    )
    @PostMapping("/{seatId}/reserve")
    fun reserveSeat(
        @PathVariable 
        @Parameter(description = "좌석 ID", required = true, example = "1")
        @Positive(message = "좌석 ID는 양수여야 합니다")
        seatId: Long
    ): ResponseEntity<CommonApiResponse<Unit>> {
        seatService.reserveSeat(seatId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = Unit,
                message = "좌석 예약 완료"
            )
        )
    }
}