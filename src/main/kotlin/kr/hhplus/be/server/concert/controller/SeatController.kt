package kr.hhplus.be.server.concert.controller

import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.concert.dto.SeatDetail
import kr.hhplus.be.server.concert.dto.SeatDto
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/seats")
@Validated
class SeatController(
    private val seatService: SeatService
) {

    /**
     * 특정 스케줄의 좌석 정보 조회
     */
    @GetMapping("/schedules/{scheduleId}")
    fun getScheduleSeats(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long
    ): ResponseEntity<CommonApiResponse<List<SeatDto>>> {
        val seats = seatService.getAvailableSeats(scheduleId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = seats,
                message = "예약 가능한 좌석 조회가 완료되었습니다"
            )
        )
    }

    /**
     * 특정 스케줄의 모든 좌석 조회
     */
    @GetMapping("/api/v1/concerts/schedules/{scheduleId}/seats/all")
    fun getAllSeats(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long
    ): ResponseEntity<CommonApiResponse<List<SeatDto>>> {
        val seats = seatService.getAllSeats(scheduleId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = seats,
                message = "모든 좌석 조회가 완료되었습니다"
            )
        )
    }

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
    @GetMapping("/api/v1/seats/{seatId}/availability")
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

    /**
     * 좌석 번호 패턴으로 검색
     */
    @GetMapping("/schedules/{scheduleId}/search")
    fun searchSeatsByPattern(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long,
        @RequestParam pattern: String
    ): ResponseEntity<CommonApiResponse<List<SeatDto>>> {
        val seats = seatService.getSeatsByNumberPattern(scheduleId, pattern)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = seats,
                message = "좌석 검색이 완료되었습니다"
            )
        )
    }

}
