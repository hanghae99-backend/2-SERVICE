package kr.hhplus.be.server.api.concert.controller

import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.api.concert.dto.*
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.ConcertStatsService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.cache.PopularConcertDto
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/concerts")
@Validated
class ConcertController(
    private val concertService: ConcertService,
    private val seatService: SeatService,
    private val concertStatsService: ConcertStatsService
) {

    @GetMapping
    fun getConcerts(
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): ResponseEntity<CommonApiResponse<List<ConcertScheduleWithInfoDto>>> {
        val start = startDate ?: LocalDate.now()
        val end = endDate ?: start.plusMonths(3)
        val concerts = concertService.getAvailableConcerts(start, end)

        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = concerts,
                message = "예약 가능한 콘서트 목록 조회 완료"
            )
        )
    }

    @GetMapping("/{concertId}")
    fun getConcert(
        @PathVariable @Positive concertId: Long
    ): ResponseEntity<CommonApiResponse<ConcertDto>> {
        val concert = concertService.getConcertById(concertId)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = concert,
                message = "콘서트 정보 조회 완료"
            )
        )
    }

    @GetMapping("/{concertId}/schedules")
    fun getConcertSchedules(
        @PathVariable @Positive concertId: Long
    ): ResponseEntity<CommonApiResponse<List<ConcertWithScheduleDto>>> {
        val schedules = concertService.getSchedulesByConcertId(concertId)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = schedules,
                message = "콘서트 스케줄 조회 완료"
            )
        )
    }

    @GetMapping("/{concertId}/schedules/{scheduleId}")
    fun getConcertSchedule(
        @PathVariable @Positive concertId: Long,
        @PathVariable @Positive scheduleId: Long
    ): ResponseEntity<CommonApiResponse<ConcertDetailDto>> {
        val detail = concertService.getConcertDetailByScheduleId(scheduleId)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = detail,
                message = "콘서트 스케줄 상세 조회 완료"
            )
        )
    }

    @GetMapping("/{concertId}/schedules/{scheduleId}/seats")
    fun getScheduleSeats(
        @PathVariable @Positive concertId: Long,
        @PathVariable @Positive scheduleId: Long,
        @RequestParam(defaultValue = "true") availableOnly: Boolean
    ): ResponseEntity<CommonApiResponse<List<SeatDto>>> {
        val seats = if (availableOnly) {
            seatService.getAvailableSeats(scheduleId)
        } else {
            seatService.getAllSeats(scheduleId)
        }

        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = seats,
                message = "좌석 조회 완료"
            )
        )
    }

    @GetMapping("/popular")
    fun getPopularConcerts(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<CommonApiResponse<List<PopularConcertDto>>> {
        val popularConcerts = concertStatsService.getPopularConcerts(limit)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = popularConcerts,
                message = "인기 콘서트 조회 완료"
            )
        )
    }

    @GetMapping("/trending")
    fun getTrendingConcerts(
        @RequestParam(defaultValue = "5") limit: Int
    ): ResponseEntity<CommonApiResponse<List<PopularConcertDto>>> {
        val trendingConcerts = concertStatsService.getTrendingConcerts(limit)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = trendingConcerts,
                message = "실시간 트렌딩 콘서트 조회 완료"
            )
        )
    }
}
