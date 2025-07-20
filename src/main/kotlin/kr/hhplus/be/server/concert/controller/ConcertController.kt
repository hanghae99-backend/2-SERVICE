package kr.hhplus.be.server.concert.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.concert.entity.SeatInfo
import kr.hhplus.be.server.concert.service.ConcertService
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.payment.dto.SeatReservationRequest
import kr.hhplus.be.server.payment.dto.SeatReservationResponse
import kr.hhplus.be.server.payment.service.ReservationService
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
    private val reservationService: ReservationService
) {

    // =============== 1. 콘서트 일정 조회 API ===============

    /**
     * 예약 가능한 콘서트 일정 목록 조회
     */
    @GetMapping
    fun getAvailableConcerts(
        @RequestParam(required = false) date: LocalDate?
    ): ResponseEntity<List<ConcertSchedule>> {
        val concerts = if (date != null) {
            // date 파라미터가 있으면 해당 날짜의 콘서트만 조회
            concertService.getConcertsByDate(date)
        } else {
            // 파라미터가 없으면 전체 예약 가능 콘서트 조회
            val start = LocalDate.now()
            val end = start.plusMonths(3)
            concertService.getAvailableConcerts(start, end)
        }
        return ResponseEntity.ok(concerts)
    }

    /**
     * 특정 콘서트의 상세 정보 조회
     */
    @GetMapping("/{concertId}")
    fun getConcert(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long
    ): ResponseEntity<ConcertSchedule> {
        val concert = concertService.getConcertById(concertId)
        return ResponseEntity.ok(concert)
    }


    // =============== 2. 콘서트 좌석 조회 API ===============

    /**
     * 특정 콘서트의 좌석 정보 조회
     */
    @GetMapping("/{concertId}/seats")
    fun getConcertSeats(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long,
        @RequestParam(required = false) status: String? // "available" 값을 받을 수 있는 파라미터 추가
    ): ResponseEntity<List<SeatInfo>> {
        val seats = if ("available" == status) {
            // status=available 파라미터가 있으면 예약 가능한 좌석만 조회
            seatService.getAvailableSeats(concertId)
        } else {
            // 파라미터가 없으면 모든 좌석 정보 조회
            seatService.getAllSeats(concertId)
        }
        return ResponseEntity.ok(seats)
    }

    /**
     * 특정 콘서트의 특정 좌석 정보 조회 (기존과 동일)
     */
    @GetMapping("/{concertId}/seats/{seatId}")
    fun getConcertSeat(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long,
        @PathVariable @Positive(message = "좌석 ID는 양수여야 합니다") seatId: Long
    ): ResponseEntity<SeatInfo> {
        val seat = seatService.getSeatById(seatId)
        return ResponseEntity.ok(seat)
    }


    // =============== 3. 콘서트 예약 API ===============
    /**
     * 콘서트 좌석 예약 생성
     */
    @PostMapping("/{concertId}/reservations")
    fun createReservation(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long,
        @Valid @RequestBody request: SeatReservationRequest
    ): ResponseEntity<SeatReservationResponse> {
        val reservation = reservationService.reserveSeat(
            request.userId,
            concertId,
            request.seatId,
            request.token
        )
        return ResponseEntity.status(201).body(SeatReservationResponse.from(reservation))
    }

    /**
     * 특정 콘서트의 예약 목록 조회 (관리자용)
     */
    @GetMapping("/{concertId}/reservations")
    fun getConcertReservations(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long
    ): ResponseEntity<List<SeatReservationResponse>> {
        val reservations = reservationService.getReservationsByConcertId(concertId)
        return ResponseEntity.ok(reservations.map { SeatReservationResponse.from(it) })
    }

    /**
     * 특정 콘서트의 특정 예약 정보 조회
     */
    @GetMapping("/{concertId}/reservations/{reservationId}")
    fun getConcertReservation(
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long,
        @PathVariable @Positive(message = "예약 ID는 양수여야 합니다") reservationId: Long
    ): ResponseEntity<SeatReservationResponse> {
        // 서비스 로직에서 concertId와 reservationId의 연관성 검증이 필요합니다.
        val reservation = reservationService.getReservationById(reservationId)
        return ResponseEntity.ok(SeatReservationResponse.from(reservation))
    }
}