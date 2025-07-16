package kr.hhplus.be.server.concert.controller

import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.concert.entity.SeatInfo
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/concerts")
class SeatController(
    private val seatService: SeatService
) {
    
    /**
     * 특정 콘서트의 예약 가능한 좌석 목록 조회
     */
    @GetMapping("/{concertId}/seats/available")
    fun getAvailableSeats(@PathVariable concertId: Long): ResponseEntity<List<SeatInfo>> {
        val availableSeats = seatService.getAvailableSeats(concertId)
        return ResponseEntity.ok(availableSeats)
    }
    
    /**
     * 특정 콘서트의 모든 좌석 정보 조회 (상태 포함)
     */
    @GetMapping("/{concertId}/seats")
    fun getAllSeats(@PathVariable concertId: Long): ResponseEntity<List<SeatInfo>> {
        val seats = seatService.getAllSeats(concertId)
        return ResponseEntity.ok(seats)
    }
    
    /**
     * 특정 좌석 정보 조회
     */
    @GetMapping("/seats/{seatId}")
    fun getSeatById(@PathVariable seatId: Long): ResponseEntity<SeatInfo> {
        val seat = seatService.getSeatById(seatId)
        return ResponseEntity.ok(seat)
    }
}
