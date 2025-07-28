package kr.hhplus.be.server.api.reservation.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.global.response.CommonApiResponse
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationConcertListRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationListRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationSearchCondition
import kr.hhplus.be.server.api.reservation.dto.request.ReservationSearchRequest
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = reservationService.reserveSeat(
            request.userId,
            request.concertId,
            request.seatId,
            request.token
        )
        return ResponseEntity.status(201).body(
            CommonApiResponse.Companion.success(
                data = ReservationDto.Companion.fromEntity(reservation),
                message = "좌석이 성공적으로 예약되었습니다."
            )
        )
    }

    @Operation(summary = "예약 확정", description = "결제 완료 후 예약을 확정합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "예약 확정 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음")
    ])
    @PutMapping("/{reservationId}/confirm")
    fun confirmReservation(
        @Parameter(description = "예약 ID", example = "1")
        @PathVariable @Positive(message = "예약 ID는 양수여야 합니다") reservationId: Long,
        @RequestParam @Positive(message = "결제 ID는 양수여야 합니다") paymentId: Long
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = reservationService.confirmReservation(reservationId, paymentId)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = ReservationDto.Companion.fromEntity(reservation),
                message = "예약이 성공적으로 확정되었습니다."
            )
        )
    }

    @Operation(summary = "예약 취소", description = "예약을 취소합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "예약 취소 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음")
    ])
    @DeleteMapping("/{reservationId}")
    fun cancelReservation(
        @Parameter(description = "예약 ID", example = "1")
        @PathVariable @Positive(message = "예약 ID는 양수여야 합니다") reservationId: Long,
        @Valid @RequestBody request: ReservationCancelRequest
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = reservationService.cancelReservation(
            reservationId,
            request.userId,
            request.cancelReason
        )
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = ReservationDto.Companion.fromEntity(reservation),
                message = "예약이 성공적으로 취소되었습니다."
            )
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
    ): ResponseEntity<CommonApiResponse<ReservationDto>> {
        val reservation = reservationService.getReservationById(reservationId)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = ReservationDto.Companion.fromEntity(reservation),
                message = "예약 정보를 성공적으로 조회했습니다."
            )
        )
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
    ): ResponseEntity<CommonApiResponse<ReservationDto.Detail>> {
        val reservation = reservationService.getReservationWithDetails(reservationId)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = ReservationDto.Detail.fromEntity(reservation),
                message = "예약 상세 정보를 성공적으로 조회했습니다"
            )
        )
    }

    @Operation(summary = "사용자별 예약 목록 조회", description = "특정 사용자의 예약 목록을 조회합니다.")
    @PostMapping("/users/{userId}")
    fun getUserReservations(
        @Parameter(description = "사용자 ID", example = "1")
        @PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long,
        @Valid @RequestBody(required = false) request: ReservationListRequest?
    ): ResponseEntity<CommonApiResponse<ReservationDto.Page>> {
        val searchCondition = ReservationSearchCondition(
            userId = userId,
            pageNumber = request?.pageNumber ?: 1,
            pageSize = request?.pageSize ?: 20,
            sortBy = request?.sortBy ?: "reservedAt",
            sortDirection = request?.sortDirection ?: "DESC"
        )
        val result = reservationService.getReservationsByCondition(searchCondition)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = result,
                message = "사용자 예약 목록 조회가 완료되었습니다"
            )
        )
    }

    @Operation(summary = "콘서트별 예약 목록 조회", description = "특정 콘서트의 예약 목록을 조회합니다. (관리자용)")
    @PostMapping("/concerts/{concertId}")
    fun getConcertReservations(
        @Parameter(description = "콘서트 ID", example = "1")
        @PathVariable @Positive(message = "콘서트 ID는 양수여야 합니다") concertId: Long,
        @Valid @RequestBody(required = false) request: ReservationConcertListRequest?
    ): ResponseEntity<CommonApiResponse<ReservationDto.Page>> {
        val searchCondition = ReservationSearchCondition(
            concertId = concertId,
            statusList = request?.statusList,
            pageNumber = request?.pageNumber ?: 1,
            pageSize = request?.pageSize ?: 20,
            sortBy = request?.sortBy ?: "reservedAt",
            sortDirection = request?.sortDirection ?: "DESC"
        )
        val result = reservationService.getReservationsByCondition(searchCondition)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = result,
                message = "콘서트 예약 목록 조회가 완료되었습니다"
            )
        )
    }

    @Operation(summary = "예약 목록 검색", description = "다양한 조건으로 예약 목록을 검색합니다.")
    @PostMapping("/search")
    fun searchReservations(
        @Valid @RequestBody request: ReservationSearchRequest
    ): ResponseEntity<CommonApiResponse<ReservationDto.Page>> {
        val result = reservationService.getReservationsByCondition(request.toSearchCondition())
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = result,
                message = "예약 검색이 완료되었습니다"
            )
        )
    }

    @Operation(summary = "만료된 예약 목록 조회", description = "만료된 임시 예약 목록을 조회합니다. (관리자용)")
    @PostMapping("/expired")
    fun getExpiredReservations(
        @Valid @RequestBody(required = false) request: ReservationListRequest?
    ): ResponseEntity<CommonApiResponse<ReservationDto.Page>> {
        val result = reservationService.getExpiredReservations(
            request?.pageNumber ?: 1,
            request?.pageSize ?: 20
        )
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = result,
                message = "만료된 예약 목록 조회가 완료되었습니다"
            )
        )
    }

    @Operation(summary = "만료된 예약 정리", description = "만료된 임시 예약들을 일괄 취소 처리합니다. (관리자용)")
    @PostMapping("/cleanup-expired")
    fun cleanupExpiredReservations(): ResponseEntity<CommonApiResponse<ReservationDto.OperationResult>> {
        val cleanupCount = reservationService.cleanupExpiredReservations()
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = ReservationDto.OperationResult(
                    message = "만료된 예약 정리가 완료되었습니다.",
                    affectedCount = cleanupCount
                ),
                message = "만료된 예약 ${cleanupCount}건이 정리되었습니다"
            )
        )
    }
}