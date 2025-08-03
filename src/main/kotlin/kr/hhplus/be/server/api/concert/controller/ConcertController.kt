package kr.hhplus.be.server.api.concert.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.api.concert.dto.*
import kr.hhplus.be.server.api.concert.dto.request.SearchConcertRequest
import kr.hhplus.be.server.api.concert.usecase.ConcertUseCase
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/concerts")
@Validated
class ConcertController(
    private val concertUseCase: ConcertUseCase,
    private val seatService: SeatService
) {


    @GetMapping
    fun getAvailableConcerts(
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): ResponseEntity<CommonApiResponse<List<ConcertScheduleWithInfoDto>>> {
        val start = startDate ?: LocalDate.now()
        val end = endDate ?: start.plusMonths(3)
        val concerts = concertUseCase.getAvailableConcerts(start, end)

        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = concerts,
                message = "예약 가능한 콘서트 목록 조회가 완료되었습니다"
            )
        )
    }


    @PostMapping("/search")
    fun searchConcerts(
        @Valid @RequestBody request: SearchConcertRequest
    ): ResponseEntity<CommonApiResponse<List<ConcertScheduleDetail>>> {
        val concerts = when {
            request.startDate != null && request.endDate != null -> {
                concertUseCase.getAvailableConcerts(request.startDate, request.endDate)
            }
            else -> {
                concertUseCase.getAvailableConcerts()
            }
        }

        val response = concerts.map { ConcertScheduleDetail.from(it) }
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = response,
                message = "콘서트 검색이 완료되었습니다"
            )
        )
    }


    @GetMapping("/{concertId}")
    fun getConcert(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long
    ): ResponseEntity<CommonApiResponse<ConcertDto>> {
        val concert = concertUseCase.getConcertById(concertId)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = concert,
                message = "콘서트 정보 조회가 완료되었습니다"
            )
        )
    }


    @GetMapping("/{concertId}/schedules")
    fun getConcertSchedules(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long,
        @RequestParam(required = false, defaultValue = "false") availableOnly: Boolean
    ): ResponseEntity<CommonApiResponse<List<ConcertWithScheduleDto>>> {
        val schedules = concertUseCase.getSchedulesByConcertId(concertId)

        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = schedules,
                message = "콘서트 스케줄 조회가 완료되었습니다"
            )
        )
    }


    @GetMapping("/schedules/{scheduleId}")
    fun getConcertSchedule(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long
    ): ResponseEntity<CommonApiResponse<ConcertDetailDto>> {
        val detail = concertUseCase.getConcertDetailByScheduleId(scheduleId)
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = detail,
                message = "콘서트 상세 정보 조회가 완료되었습니다"
            )
        )
    }


    @GetMapping("/schedules/{scheduleId}/seats")
    fun getScheduleSeats(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long,
        @RequestParam(required = false, defaultValue = "true") availableOnly: Boolean,
        @RequestParam(required = false) seatNumberPattern: String?
    ): ResponseEntity<CommonApiResponse<List<SeatDto>>> {
        val seats = when {
            seatNumberPattern != null -> {
                seatService.getSeatsByNumberPattern(scheduleId, seatNumberPattern)
            }
            availableOnly -> {
                seatService.getAvailableSeats(scheduleId)
            }
            else -> {
                seatService.getAllSeats(scheduleId)
            }
        }

        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = seats,
                message = "좌석 조회가 완료되었습니다"
            )
        )
    }
}