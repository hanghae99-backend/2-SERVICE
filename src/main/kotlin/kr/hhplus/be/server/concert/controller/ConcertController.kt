package kr.hhplus.be.server.concert.controller

import kr.hhplus.be.server.concert.service.ConcertService
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/concerts")
class ConcertController(
    private val concertService: ConcertService
) {
    
    /**
     * 예약 가능한 콘서트 목록 조회
     */
    @GetMapping("/available")
    fun getAvailableConcerts(
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): ResponseEntity<List<ConcertSchedule>> {
        val start = startDate ?: LocalDate.now()
        val end = endDate ?: LocalDate.now().plusMonths(3)
        val concerts = concertService.getAvailableConcerts(start, end)
        return ResponseEntity.ok(concerts)
    }
    
    /**
     * 특정 날짜의 콘서트 목록 조회
     */
    @GetMapping("/by-date")
    fun getConcertsByDate(@RequestParam date: LocalDate): ResponseEntity<List<ConcertSchedule>> {
        val concerts = concertService.getConcertsByDate(date)
        return ResponseEntity.ok(concerts)
    }
    
    /**
     * 콘서트 상세 정보 조회
     */
    @GetMapping("/{concertId}")
    fun getConcertById(@PathVariable concertId: Long): ResponseEntity<ConcertSchedule> {
        val concert = concertService.getConcertById(concertId)
        return ResponseEntity.ok(concert)
    }
}
