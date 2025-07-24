package kr.hhplus.be.server.concert.controller

import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.concert.dto.SeatDetail
import kr.hhplus.be.server.concert.service.SeatService
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
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long,
        @RequestParam(required = false, defaultValue = "false") availableOnly: Boolean
    ): ResponseEntity<List<SeatDetail>> {
        val seats = if (availableOnly) {
            seatService.getAvailableSeats(scheduleId)
        } else {
            seatService.getAllSeats(scheduleId)
        }
        
        val response = seats.map { SeatDetail.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * 특정 좌석 정보 조회
     */
    @GetMapping("/{seatId}")
    fun getSeat(
        @PathVariable @Positive(message = "좌석 ID는 양수여야 합니다") seatId: Long
    ): ResponseEntity<SeatDetail> {
        val seat = seatService.getSeatById(seatId)
        return ResponseEntity.ok(SeatDetail.from(seat))
    }

    /**
     * 가격 범위로 예약 가능한 좌석 조회
     */
    @GetMapping("/schedules/{scheduleId}/price-range")
    fun getSeatsByPriceRange(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long,
        @RequestParam minPrice: BigDecimal,
        @RequestParam maxPrice: BigDecimal
    ): ResponseEntity<List<SeatDetail>> {
        val seats = seatService.getAvailableSeatsByPriceRange(scheduleId, minPrice, maxPrice)
        val response = seats.map { SeatDetail.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * 특정 가격 이하의 예약 가능한 좌석 조회
     */
    @GetMapping("/schedules/{scheduleId}/under-price")
    fun getSeatsUnderPrice(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long,
        @RequestParam maxPrice: BigDecimal
    ): ResponseEntity<List<SeatDetail>> {
        val seats = seatService.getAvailableSeatsUnderPrice(scheduleId, maxPrice)
        val response = seats.map { SeatDetail.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * 좌석 번호 패턴으로 검색
     */
    @GetMapping("/schedules/{scheduleId}/search")
    fun searchSeatsByPattern(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long,
        @RequestParam pattern: String
    ): ResponseEntity<List<SeatDetail>> {
        val seats = seatService.getSeatsByNumberPattern(scheduleId, pattern)
        val response = seats.map { SeatDetail.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * 좌석 예약 가능 여부 확인
     */
    @GetMapping("/{seatId}/availability")
    fun checkSeatAvailability(
        @PathVariable @Positive(message = "좌석 ID는 양수여야 합니다") seatId: Long
    ): ResponseEntity<Map<String, Boolean>> {
        val isAvailable = seatService.isSeatAvailable(seatId)
        return ResponseEntity.ok(mapOf("available" to isAvailable))
    }

    /**
     * 스케줄별 좌석 통계 조회
     */
    @GetMapping("/schedules/{scheduleId}/statistics")
    fun getSeatStatistics(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long
    ): ResponseEntity<Map<String, Int>> {
        val availableCount = seatService.countAvailableSeats(scheduleId)
        val statistics = mapOf(
            "availableSeats" to availableCount
        )
        return ResponseEntity.ok(statistics)
    }
}
