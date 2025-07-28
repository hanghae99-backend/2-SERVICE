package kr.hhplus.be.server.api.concert.controller

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
class SeatController(
    private val seatService: SeatService
) {

    /**
     * 특정 좌석 정보 조회
     */
    @GetMapping("/{seatId}")
    fun getSeat(
        @PathVariable @Positive(message = "좌석 ID는 양수여야 합니다") seatId: Long
    ): ResponseEntity<CommonApiResponse<SeatDto>> {
        val seat = seatService.getSeatById(seatId)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = seat,
                message = "좌석 정보 조회가 완료되었습니다"
            )
        )
    }

    /**
     * 좌석 예약 가능 여부 확인
     */
    @GetMapping("/{seatId}/availability")
    fun checkSeatAvailability(
        @PathVariable @Positive(message = "좌석 ID는 양수여야 합니다") seatId: Long
    ): ResponseEntity<CommonApiResponse<Boolean>> {
        val isAvailable = seatService.isSeatAvailable(seatId)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = isAvailable,
                message = "좌석 가용성 확인이 완료되었습니다"
            )
        )
    }
}