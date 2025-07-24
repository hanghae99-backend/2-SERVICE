package kr.hhplus.be.server.concert.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.concert.dto.*
import kr.hhplus.be.server.concert.dto.request.SearchConcertRequest
import kr.hhplus.be.server.concert.service.ConcertService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/concerts")
@Validated
class ConcertController(
    private val concertService: ConcertService
) {

    /**
     * 예약 가능한 콘서트 일정 목록 조회
     */
    @GetMapping
    fun getAvailableConcerts(
        @RequestParam(required = false) date: LocalDate?
    ): ResponseEntity<List<ConcertScheduleDetail>> {
        val concerts = if (date != null) {
            concertService.getConcertsByDate(date)
        } else {
            val start = LocalDate.now()
            val end = start.plusMonths(3)
            concertService.getAvailableConcerts(start, end)
        }
        
        val response = concerts.map { ConcertScheduleDetail.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * 콘서트 검색
     */
    @PostMapping("/search")
    fun searchConcerts(
        @Valid @RequestBody request: SearchConcertRequest
    ): ResponseEntity<List<ConcertScheduleDetail>> {
        val concerts = when {
            request.startDate != null && request.endDate != null -> {
                concertService.getAvailableConcerts(request.startDate, request.endDate)
            }
            else -> {
                concertService.getAvailableConcerts()
            }
        }
        
        val response = concerts.map { ConcertScheduleDetail.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * 특정 콘서트의 상세 정보 조회
     */
    @GetMapping("/{concertId}")
    fun getConcert(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long
    ): ResponseEntity<ConcertDetail> {
        val concert = concertService.getConcertById(concertId)
        return ResponseEntity.ok(ConcertDetail.from(concert))
    }
    
    /**
     * 특정 콘서트의 스케줄 목록 조회
     */
    @GetMapping("/{concertId}/schedules")
    fun getConcertSchedules(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long,
        @RequestParam(required = false, defaultValue = "false") availableOnly: Boolean
    ): ResponseEntity<List<ConcertScheduleDetail>> {
        val schedules = if (availableOnly) {
            concertService.getAvailableSchedulesByConcertId(concertId)
        } else {
            concertService.getSchedulesByConcertId(concertId)
        }
        
        val response = schedules.map { ConcertScheduleDetail.from(it) }
        return ResponseEntity.ok(response)
    }
    
    /**
     * 특정 스케줄의 상세 정보 조회
     */
    @GetMapping("/schedules/{scheduleId}")
    fun getConcertSchedule(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long
    ): ResponseEntity<ConcertScheduleDetail> {
        val schedule = concertService.getConcertScheduleById(scheduleId)
        return ResponseEntity.ok(ConcertScheduleDetail.from(schedule))
    }
    
    /**
     * 특정 스케줄의 상세 정보 조회 (좌석 포함)
     */
    @GetMapping("/schedules/{scheduleId}/detail")
    fun getConcertDetail(
        @PathVariable @Positive(message = "스케줄 ID는 양수여야 합니다") scheduleId: Long
    ): ResponseEntity<ConcertFullDetail> {
        val detail = concertService.getConcertDetailByScheduleId(scheduleId)
        return ResponseEntity.ok(ConcertFullDetail.from(detail))
    }
}
