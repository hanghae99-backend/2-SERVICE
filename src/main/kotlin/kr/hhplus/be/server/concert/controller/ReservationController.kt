package kr.hhplus.be.server.concert.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.payment.dto.SeatReservationRequest
import kr.hhplus.be.server.payment.dto.SeatReservationResponse
import kr.hhplus.be.server.payment.service.ReservationService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/concerts")
@Validated
class ReservationController(
    private val reservationService: ReservationService
) {
    
    /**
     * 좌석 예약 요청 API
     * 요구사항 3️⃣: 날짜와 좌석 정보를 입력받아 좌석을 예약 처리
     */
    @PostMapping("/reservations")
    fun reserveSeat(@Valid @RequestBody request: SeatReservationRequest): ResponseEntity<SeatReservationResponse> {
        val reservation = reservationService.reserveSeat(
            request.userId,
            request.concertId, 
            request.seatId,
            request.token
        )
        return ResponseEntity.ok(SeatReservationResponse.from(reservation))
    }
    
    /**
     * 사용자의 예약 목록 조회
     */
    @GetMapping("/reservations/user/{userId}")
    fun getUserReservations(
        @PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<List<SeatReservationResponse>> {
        val reservations = reservationService.getReservationsByUserId(userId)
        return ResponseEntity.ok(reservations.map { SeatReservationResponse.from(it) })
    }
    
    /**
     * 특정 예약 정보 조회
     */
    @GetMapping("/reservations/{reservationId}")
    fun getReservation(
        @PathVariable @Positive(message = "예약 ID는 양수여야 합니다") reservationId: Long
    ): ResponseEntity<SeatReservationResponse> {
        val reservation = reservationService.getReservationById(reservationId)
        return ResponseEntity.ok(SeatReservationResponse.from(reservation))
    }
}
