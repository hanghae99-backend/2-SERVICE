package kr.hhplus.be.server.concert.controller.mock

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.concert.dto.*
import kr.hhplus.be.server.payment.dto.SeatReservationRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/mock/concerts")
@Tag(name = "콘서트 예약 (Mock)", description = "콘서트 예약 관리 Mock API - 개발/테스트용")
class MockConcertController {
    
    @GetMapping("/dates")
    @Operation(
        summary = "예약 가능한 날짜 조회",
        description = """
            **예약 가능한 콘서트 날짜 목록을 조회합니다**
            
            ## 응답 정보
            - 콘서트 기본 정보 (제목, 아티스트)
            - 공연 일시 및 시간
            - 좌석 현황 (전체/사용가능)
            
            ## 활용 방법
            1. 원하는 콘서트 선택
            2. 해당 콘서트의 좌석 조회
            3. 좌석 예약 진행
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = AvailableDateResponse::class))
                )]
            )
        ]
    )
    fun getAvailableDates(): ResponseEntity<List<AvailableDateResponse>> {
        val dates = listOf(
            AvailableDateResponse(
                concertId = 1L,
                title = "아이유 콘서트",
                artist = "아이유",
                concertDate = "2024-12-25",
                startTime = "19:00",
                availableSeats = 45,
                totalSeats = 50
            ),
            AvailableDateResponse(
                concertId = 2L,
                title = "BTS 콘서트",
                artist = "BTS",
                concertDate = "2024-12-31",
                startTime = "20:00",
                availableSeats = 30,
                totalSeats = 50
            ),
            AvailableDateResponse(
                concertId = 3L,
                title = "NewJeans 콘서트",
                artist = "NewJeans",
                concertDate = "2025-01-15",
                startTime = "18:00",
                availableSeats = 50,
                totalSeats = 50
            )
        )
        
        return ResponseEntity.ok(dates)
    }
    
    @GetMapping("/{concertId}/seats")
    @Operation(
        summary = "좌석 조회",
        description = """
            **특정 콘서트의 좌석 정보를 조회합니다**
            
            ## 좌석 상태
            - `AVAILABLE`: 예약 가능
            - `RESERVED`: 예약 완료
            - `TEMPORARY_HOLD`: 임시 예약 (5분간)
            
            ## 좌석 번호
            - 1번부터 50번까지 오름차순
            - 모든 좌석 동일 가격
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = SeatResponse::class))
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "콘서트를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "콘서트 없음",
                            value = """{"error": "콘서트를 찾을 수 없습니다", "code": "CONCERT_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun getSeats(@PathVariable @Parameter(description = "콘서트 ID", example = "1") concertId: Long): ResponseEntity<List<SeatResponse>> {
        val seats = (1..50).map { seatNumber ->
            val statuses = listOf("AVAILABLE", "RESERVED", "TEMPORARY_HOLD")
            val status = statuses.random()
            
            SeatResponse(
                seatId = seatNumber.toLong(),
                seatNumber = seatNumber,
                price = BigDecimal("100000"),
                status = status,
                temporaryHoldExpiresAt = if (status == "TEMPORARY_HOLD") {
                    LocalDateTime.now().plusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                } else null
            )
        }
        
        return ResponseEntity.ok(seats)
    }
    
    @PostMapping("/reservations")
    @Operation(
        summary = "좌석 예약",
        description = """
            **좌석을 임시 예약합니다**
            
            ## 예약 프로세스
            1. 좌석 가용성 확인
            2. 임시 예약 (5분간)
            3. 결제 완료로 확정
            
            ## 주의사항
            - 임시 예약은 5분 후 자동 만료
            - 다른 사용자도 동시에 예약 시도 가능
            - 선착순 예약 시스템
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "예약 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SeatReservationResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "예약 실패 (이미 예약된 좌석 등)",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "좌석 예약 실패",
                            value = """{"error": "이미 예약된 좌석입니다", "code": "SEAT_ALREADY_RESERVED"}"""
                        )
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "좌석을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "좌석 없음",
                            value = """{"error": "좌석을 찾을 수 없습니다", "code": "SEAT_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun reserveSeat(@RequestBody @Parameter(description = "좌석 예약 요청 정보") request: SeatReservationRequest): ResponseEntity<SeatReservationResponse> {
        val now = LocalDateTime.now()
        val expiresAt = now.plusMinutes(5)
        
        val seat = SeatResponse(
            seatId = request.seatId,
            seatNumber = request.seatId.toInt(),
            price = BigDecimal("100000"),
            status = "TEMPORARY_HOLD",
            temporaryHoldExpiresAt = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        
        val response = SeatReservationResponse(
            reservationId = (1..1000).random().toLong(),
            userId = request.userId,
            seat = seat,
            status = "TEMPORARY",
            createdAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            expiresAt = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/reservations/{userId}")
    @Operation(
        summary = "사용자 예약 조회",
        description = """
            **사용자의 예약 내역을 조회합니다**
            
            ## 예약 상태
            - `TEMPORARY`: 임시 예약 (5분간)
            - `CONFIRMED`: 결제 완료된 예약
            - `EXPIRED`: 만료된 예약
            - `CANCELLED`: 취소된 예약
            
            ## 정렬 순서
            - 최신 예약 순으로 정렬
            - 임시 예약의 만료 시간 표시
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = SeatReservationResponse::class))
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "사용자 없음",
                            value = """{"error": "사용자를 찾을 수 없습니다", "code": "USER_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun getUserReservations(@PathVariable @Parameter(description = "사용자 ID", example = "1") userId: Long): ResponseEntity<List<SeatReservationResponse>> {
        val reservations = (1..3).map { index ->
            val now = LocalDateTime.now().minusHours(index.toLong())
            val seat = SeatResponse(
                seatId = index.toLong(),
                seatNumber = index,
                price = BigDecimal("100000"),
                status = if (index == 1) "TEMPORARY_HOLD" else "RESERVED",
                temporaryHoldExpiresAt = if (index == 1) now.plusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) else null
            )
            
            SeatReservationResponse(
                reservationId = index.toLong(),
                userId = userId,
                seat = seat,
                status = if (index == 1) "TEMPORARY" else "CONFIRMED",
                createdAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                expiresAt = if (index == 1) now.plusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) else now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        }
        
        return ResponseEntity.ok(reservations)
    }
}
