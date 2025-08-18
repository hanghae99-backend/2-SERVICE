package kr.hhplus.be.server.api.concert.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.api.concert.dto.*
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.ConcertStatsService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/concerts")
@Validated
@Tag(name = "Concert", description = "콘서트 관리 API")
class ConcertController(
    private val concertService: ConcertService,
    private val seatService: SeatService,
    private val concertStatsService: ConcertStatsService
) {

    @Operation(
        summary = "예약 가능한 콘서트 목록 조회",
        description = "지정된 기간 내 예약 가능한 콘서트 목록을 조회합니다."
    )
    @GetMapping
    fun getConcerts(
        @RequestParam(required = false) 
        @Parameter(description = "조회 시작일", example = "2024-01-01")
        startDate: LocalDate?,
        
        @RequestParam(required = false) 
        @Parameter(description = "조회 종료일", example = "2024-04-01")
        endDate: LocalDate?
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

    @Operation(
        summary = "콘서트 상세 정보 조회",
        description = "특정 콘서트의 상세 정보를 조회합니다."
    )
    @GetMapping("/{concertId}")
    fun getConcert(
        @PathVariable 
        @Parameter(description = "콘서트 ID", required = true, example = "1")
        @Positive(message = "콘서트 ID는 양수여야 합니다")
        concertId: Long
    ): ResponseEntity<CommonApiResponse<ConcertDto>> {
        val concert = concertService.getConcertById(concertId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = concert,
                message = "콘서트 정보 조회 완료"
            )
        )
    }

    @Operation(
        summary = "콘서트 스케줄 목록 조회",
        description = "특정 콘서트의 모든 스케줄 목록을 조회합니다."
    )
    @GetMapping("/{concertId}/schedules")
    fun getConcertSchedules(
        @PathVariable 
        @Parameter(description = "콘서트 ID", required = true, example = "1")
        @Positive(message = "콘서트 ID는 양수여야 합니다")
        concertId: Long
    ): ResponseEntity<CommonApiResponse<List<ConcertWithScheduleDto>>> {
        val schedules = concertService.getSchedulesByConcertId(concertId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = schedules,
                message = "콘서트 스케줄 조회 완료"
            )
        )
    }

    @Operation(
        summary = "좌석 목록 조회",
        description = "특정 콘서트 스케줄의 좌석 목록을 조회합니다."
    )
    @GetMapping("/{concertId}/schedules/{scheduleId}/seats")
    fun getScheduleSeats(
        @PathVariable 
        @Parameter(description = "콘서트 ID", required = true, example = "1")
        @Positive(message = "콘서트 ID는 양수여야 합니다")
        concertId: Long,
        
        @PathVariable 
        @Parameter(description = "스케줄 ID", required = true, example = "1")
        @Positive(message = "스케줄 ID는 양수여야 합니다")
        scheduleId: Long,
        
        @RequestParam(defaultValue = "true") 
        @Parameter(description = "예약 가능한 좌석만 조회 여부", example = "true")
        availableOnly: Boolean
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

    @Operation(
        summary = "인기 콘서트 조회",
        description = "예약률이 높은 인기 콘서트 목록을 조회합니다."
    )
    @GetMapping("/popular")
    fun getPopularConcerts(
        @RequestParam(defaultValue = "10") 
        @Parameter(description = "조회할 콘서트 수", example = "10")
        @Min(value = 1, message = "최소 1개 이상이어야 합니다")
        @Max(value = 50, message = "최대 50개까지 조회 가능합니다")
        limit: Int
    ): ResponseEntity<CommonApiResponse<List<PopularConcertDto>>> {
        val popularConcerts = concertStatsService.getPopularConcerts(limit)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = popularConcerts,
                message = "인기 콘서트 조회 완료"
            )
        )
    }

    @Operation(
        summary = "실시간 트렌딩 콘서트 조회",
        description = "최근 검색량과 예약률이 급상승한 트렌딩 콘서트 목록을 조회합니다."
    )
    @GetMapping("/trending")
    fun getTrendingConcerts(
        @RequestParam(defaultValue = "5") 
        @Parameter(description = "조회할 콘서트 수", example = "5")
        @Min(value = 1, message = "최소 1개 이상이어야 합니다")
        @Max(value = 20, message = "최대 20개까지 조회 가능합니다")
        limit: Int
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
