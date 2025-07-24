package kr.hhplus.be.server.reservation.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.reservation.dto.ReservationDto
import kr.hhplus.be.server.reservation.dto.request.*
import kr.hhplus.be.server.reservation.service.ReservationService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses

@RestController
@RequestMapping("/api/v1/reservations")
@Validated
@Tag(name = "Reservation", description = "예약 관리 API")
class ReservationController(
    private val reservationService: ReservationService
) {

    @Operation(summary = "좌석 예약 생성", description = "사용자가 선택한 좌석을 예약합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "예약 생성 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "409", description = "이미 예약된 좌석")
    ])
    @PostMapping
    fun createReservation(
        @Valid @RequestBody request: ReservationCreateRequest
    ): ResponseEntity<ReservationDto.WithMessage> {
        val reservation = reservationService.reserveSeat(
            request.userId,
            request.concertId,
            request.seatId,
            request.token
        )
        return ResponseEntity.status(201).body(
            ReservationDto.WithMessage.fromEntity(reservation, "좌석이 성공적으로 예약되었습니다.")
        )
    }

    @Operation(summary = "예약 확정", description = "결제 완료 후 예약을 확정합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "예약 확정 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음")
    ])
    @PutMapping("/confirm")
    fun confirmReservation(
        @Valid @RequestBody request: ReservationConfirmRequest
    ): ResponseEntity<ReservationDto.WithMessage> {
        val reservation = reservationService.confirmReservation(
            request.reservationId,
            request.paymentId
        )
        return ResponseEntity.ok(
            ReservationDto.WithMessage.fromEntity(reservation, "예약이 성공적으로 확정되었습니다.")
        )
    }

    @Operation(summary = "예약 취소", description = "예약을 취소합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "예약 취소 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음")
    ])
    @PutMapping("/cancel")
    fun cancelReservation(
        @Valid @RequestBody request: ReservationCancelRequest
    ): ResponseEntity<ReservationDto.WithMessage> {
        val reservation = reservationService.cancelReservation(
            request.reservationId,
            request.userId,
            request.cancelReason
        )
        return ResponseEntity.ok(
            ReservationDto.WithMessage.fromEntity(reservation, "예약이 성공적으로 취소되었습니다.")
        )
    }

    @Operation(summary = "특정 예약 조회", description = "예약 ID로 특정 예약 정보를 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음")
    ])
    @GetMapping("/{reservationId}")
    fun getReservation(
        @Parameter(description = "예약 ID", example = "1")
        @PathVariable @Positive(message = "예약 ID는 양수여야 합니다") reservationId: Long
    ): ResponseEntity<ReservationDto> {
        val reservation = reservationService.getReservationById(reservationId)
        return ResponseEntity.ok(ReservationDto.fromEntity(reservation))
    }

    @Operation(summary = "예약 상세 정보 조회", description = "연관 엔티티 정보를 포함한 예약 상세 정보를 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음")
    ])
    @GetMapping("/{reservationId}/detail")
    fun getReservationDetail(
        @Parameter(description = "예약 ID", example = "1")
        @PathVariable @Positive(message = "예약 ID는 양수여야 합니다") reservationId: Long
    ): ResponseEntity<ReservationDto.Detail> {
        val reservation = reservationService.getReservationWithDetails(reservationId)
        return ResponseEntity.ok(ReservationDto.Detail.fromEntity(reservation))
    }

    @Operation(summary = "사용자별 예약 목록 조회", description = "특정 사용자의 예약 목록을 조회합니다.")
    @PostMapping("/users/{userId}")
    fun getUserReservations(
        @Parameter(description = "사용자 ID", example = "1")
        @PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long,
        @Valid @RequestBody(required = false) request: ReservationListRequest?
    ): ResponseEntity<ReservationDto.Page> {
        val searchCondition = ReservationSearchCondition(
            userId = userId,
            pageNumber = request?.pageNumber ?: 1,
            pageSize = request?.pageSize ?: 20,
            sortBy = request?.sortBy ?: "reservedAt",
            sortDirection = request?.sortDirection ?: "DESC"
        )
        val result = reservationService.getReservationsByCondition(searchCondition)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "콘서트별 예약 목록 조회", description = "특정 콘서트의 예약 목록을 조회합니다. (관리자용)")
    @PostMapping("/concerts/{concertId}")
    fun getConcertReservations(
        @Parameter(description = "콘서트 ID", example = "1")
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long,
        @Valid @RequestBody(required = false) request: ReservationConcertListRequest?
    ): ResponseEntity<ReservationDto.Page> {
        val searchCondition = ReservationSearchCondition(
            concertId = concertId,
            statusList = request?.statusList,
            pageNumber = request?.pageNumber ?: 1,
            pageSize = request?.pageSize ?: 20,
            sortBy = request?.sortBy ?: "reservedAt",
            sortDirection = request?.sortDirection ?: "DESC"
        )
        val result = reservationService.getReservationsByCondition(searchCondition)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "예약 목록 검색", description = "다양한 조건으로 예약 목록을 검색합니다.")
    @PostMapping("/search")
    fun searchReservations(
        @Valid @RequestBody request: ReservationSearchRequest
    ): ResponseEntity<ReservationDto.Page> {
        val result = reservationService.getReservationsByCondition(request.toSearchCondition())
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "예약 통계 조회", description = "예약 관련 통계 정보를 조회합니다. (관리자용)")
    @PostMapping("/stats")
    fun getReservationStats(
        @Valid @RequestBody(required = false) request: ReservationStatsRequest?
    ): ResponseEntity<ReservationDto.Statistics> {
        val stats = reservationService.getReservationStats(
            request?.concertId,
            request?.startDate,
            request?.endDate
        )
        return ResponseEntity.ok(stats)
    }

    @Operation(summary = "만료된 예약 목록 조회", description = "만료된 임시 예약 목록을 조회합니다. (관리자용)")
    @PostMapping("/expired")
    fun getExpiredReservations(
        @Valid @RequestBody(required = false) request: ReservationListRequest?
    ): ResponseEntity<ReservationDto.Page> {
        val result = reservationService.getExpiredReservations(
            request?.pageNumber ?: 1,
            request?.pageSize ?: 20
        )
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "만료된 예약 정리", description = "만료된 임시 예약들을 일괄 취소 처리합니다. (관리자용)")
    @PostMapping("/cleanup-expired")
    fun cleanupExpiredReservations(): ResponseEntity<ReservationDto.OperationResult> {
        val cleanupCount = reservationService.cleanupExpiredReservations()
        return ResponseEntity.ok(
            ReservationDto.OperationResult(
                message = "만료된 예약 정리가 완료되었습니다.",
                affectedCount = cleanupCount
            )
        )
    }
}
