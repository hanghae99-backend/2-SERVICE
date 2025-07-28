package kr.hhplus.be.server.reservation.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.api.reservation.controller.ReservationController
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.reservation.dto.request.ReservationListRequest
import kr.hhplus.be.server.reservation.dto.request.ReservationSearchRequest
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.reservation.entity.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.exception.ReservationNotFoundException
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(ReservationController::class)
class ReservationControllerTest : DescribeSpec({
    
    val reservationService = mockk<ReservationService>()
    val reservationController = ReservationController(reservationService)
    val mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = ObjectMapper()
    
    describe("POST /api/v1/reservations") {
        context("유효한 예약 생성 요청이 들어올 때") {
            it("예약을 생성하고 201 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "test-token"
                val request = ReservationCreateRequest(userId, concertId, seatId, token)
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val reservation = Reservation(
                    reservationId = 1L,
                    userId = userId,
                    concertId = concertId,
                    seatId = seatId,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    status = temporaryStatus,
                    reservedAt = LocalDateTime.now()
                )
                
                every { reservationService.reserveSeat(userId, concertId, seatId, token) } returns reservation
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("좌석이 성공적으로 예약되었습니다."))
                    .andExpect(jsonPath("$.data.reservationId").value(1L))
                    .andExpect(jsonPath("$.data.userId").value(userId))
                    .andExpect(jsonPath("$.data.concertId").value(concertId))
                    .andExpect(jsonPath("$.data.seatId").value(seatId))
                    .andExpect(jsonPath("$.data.seatNumber").value("A1"))
                    .andExpect(jsonPath("$.data.price").value(50000))
                    .andDo(print())
            }
        }
        
        context("잘못된 형식의 요청이 들어올 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val invalidRequest = """{"userId": -1, "concertId": 1, "seatId": 1, "token": ""}"""
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest)
                )
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
        
        context("이미 예약된 좌석으로 요청할 때") {
            it("409 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "test-token"
                val request = ReservationCreateRequest(userId, concertId, seatId, token)
                
                every { reservationService.reserveSeat(userId, concertId, seatId, token) } throws
                        IllegalStateException("이미 예약된 좌석입니다")
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isConflict)
                    .andDo(print())
            }
        }
    }
    
    describe("PUT /api/v1/reservations/{reservationId}/confirm") {
        context("유효한 예약 확정 요청이 들어올 때") {
            it("예약을 확정하고 200 상태코드를 반환해야 한다") {
                // given
                val reservationId = 1L
                val paymentId = 1L
                val confirmedStatus = ReservationStatusType("CONFIRMED", "확정", "확정된 예약 상태", true, LocalDateTime.now())
                val reservation = Reservation(
                    reservationId = reservationId,
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    status = confirmedStatus,
                    reservedAt = LocalDateTime.now()
                )
                reservation.paymentId = paymentId
                
                every { reservationService.confirmReservation(reservationId, paymentId) } returns reservation
                
                // when & then
                mockMvc.perform(
                    put("/api/v1/reservations/$reservationId/confirm")
                        .param("paymentId", paymentId.toString())
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("예약이 성공적으로 확정되었습니다."))
                    .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                    .andExpect(jsonPath("$.data.paymentId").value(paymentId))
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 예약으로 확정 요청할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val reservationId = 999L
                val paymentId = 1L
                
                every { reservationService.confirmReservation(reservationId, paymentId) } throws
                        ReservationNotFoundException("예약을 찾을 수 없습니다: $reservationId")
                
                // when & then
                mockMvc.perform(
                    put("/api/v1/reservations/$reservationId/confirm")
                        .param("paymentId", paymentId.toString())
                )
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/reservations/{reservationId}") {
        context("존재하는 예약 ID로 조회할 때") {
            it("예약 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val reservationId = 1L
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val reservation = Reservation(
                    reservationId = reservationId,
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    status = temporaryStatus,
                    reservedAt = LocalDateTime.now()
                )
                
                every { reservationService.getReservationById(reservationId) } returns reservation
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/reservations/$reservationId")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("예약 정보를 성공적으로 조회했습니다."))
                    .andExpect(jsonPath("$.data").exists())
                    .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                    .andExpect(jsonPath("$.data.userId").value(1L))
                    .andExpect(jsonPath("$.data.seatNumber").value("A1"))
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 예약 ID로 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val reservationId = 999L
                
                every { reservationService.getReservationById(reservationId) } throws
                        ReservationNotFoundException("예약을 찾을 수 없습니다: $reservationId")
                
                // when & then
                mockMvc.perform(get("/api/v1/reservations/$reservationId"))
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/reservations/{reservationId}/detail") {
        context("존재하는 예약 ID로 상세 조회할 때") {
            it("예약 상세 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val reservationId = 1L
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val reservation = Reservation(
                    reservationId = reservationId,
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    status = temporaryStatus
                )
                
                every { reservationService.getReservationWithDetails(reservationId) } returns reservation
                
                // when & then
                mockMvc.perform(get("/api/v1/reservations/$reservationId/detail"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("예약 상세 정보를 성공적으로 조회했습니다"))
                    .andExpect(jsonPath("$.data.reservation.reservationId").value(reservationId))
                    .andDo(print())
            }
        }
    }
    
    describe("POST /api/v1/reservations/users/{userId}") {
        context("유효한 사용자 ID로 예약 목록 조회할 때") {
            it("사용자 예약 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val request = ReservationListRequest(
                    pageNumber = 1,
                    pageSize = 10,
                    sortBy = "reservedAt",
                    sortDirection = "DESC"
                )
                val mockPage = ReservationDto.Page(
                    reservations = emptyList(),
                    totalCount = 0,
                    pageNumber = 1,
                    pageSize = 10,
                    totalPages = 0
                )
                
                every { reservationService.getReservationsByCondition(any()) } returns mockPage
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/reservations/users/$userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("사용자 예약 목록 조회가 완료되었습니다"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andExpect(jsonPath("$.data.totalCount").value(0))
                    .andDo(print())
            }
        }
    }
    
    describe("POST /api/v1/reservations/search") {
        context("유효한 검색 조건으로 예약 검색할 때") {
            it("검색 결과를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val searchRequest = ReservationSearchRequest(
                    userId = 1L,
                    pageNumber = 1,
                    pageSize = 10
                )
                val mockPage = ReservationDto.Page(
                    reservations = emptyList(),
                    totalCount = 0,
                    pageNumber = 1,
                    pageSize = 10,
                    totalPages = 0
                )
                
                every { reservationService.getReservationsByCondition(any()) } returns mockPage
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/reservations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("예약 검색이 완료되었습니다"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andDo(print())
            }
        }
    }
    
    describe("POST /api/v1/reservations/expired") {
        context("만료된 예약 목록 조회할 때") {
            it("만료된 예약 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val request = ReservationListRequest(
                    pageNumber = 1,
                    pageSize = 10
                )
                val mockPage = ReservationDto.Page(
                    reservations = emptyList(),
                    totalCount = 0,
                    pageNumber = 1,
                    pageSize = 10,
                    totalPages = 0
                )
                
                every { reservationService.getExpiredReservations(1, 10) } returns mockPage
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/reservations/expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("만료된 예약 목록 조회가 완료되었습니다"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andDo(print())
            }
        }
    }
    
    describe("POST /api/v1/reservations/cleanup-expired") {
        context("만료된 예약 정리 요청할 때") {
            it("정리 결과를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val cleanupCount = 5
                
                every { reservationService.cleanupExpiredReservations() } returns cleanupCount
                
                // when & then
                mockMvc.perform(post("/api/v1/reservations/cleanup-expired"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("만료된 예약 ${cleanupCount}건이 정리되었습니다"))
                    .andExpect(jsonPath("$.data.affectedCount").value(cleanupCount))
                    .andExpect(jsonPath("$.data.message").value("만료된 예약 정리가 완료되었습니다."))
                    .andDo(print())
            }
        }
    }
    
    describe("DELETE /api/v1/reservations/{reservationId}") {
        context("유효한 예약 취소 요청이 들어올 때") {
            it("예약을 취소하고 200 상태코드를 반환해야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val reason = "사용자 요청"
                val request = ReservationCancelRequest(userId, reason)
                val cancelledStatus = ReservationStatusType("CANCELLED", "취소", "취소된 예약 상태", true, LocalDateTime.now())
                val reservation = Reservation(
                    reservationId = reservationId,
                    userId = userId,
                    concertId = 1L,
                    seatId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    status = cancelledStatus,
                    reservedAt = LocalDateTime.now()
                )
                
                every { reservationService.cancelReservation(reservationId, userId, reason) } returns reservation
                
                // when & then
                mockMvc.perform(
                    delete("/api/v1/reservations/$reservationId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("예약이 성공적으로 취소되었습니다."))
                    .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                    .andDo(print())
            }
        }
        
        context("다른 사용자의 예약을 취소하려 할 때") {
            it("403 상태코드를 반환해야 한다") {
                // given
                val reservationId = 1L
                val userId = 2L
                val reason = "사용자 요청"
                val request = ReservationCancelRequest(userId, reason)
                
                every { reservationService.cancelReservation(reservationId, userId, reason) } throws
                        IllegalArgumentException("예약 소유자가 아닙니다")
                
                // when & then
                mockMvc.perform(
                    delete("/api/v1/reservations/$reservationId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isForbidden)
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 예약을 취소하려 할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val reservationId = 999L
                val userId = 1L
                val reason = "사용자 요청"
                val request = ReservationCancelRequest(userId, reason)
                
                every { reservationService.cancelReservation(reservationId, userId, reason) } throws
                        ReservationNotFoundException("예약을 찾을 수 없습니다: $reservationId")
                
                // when & then
                mockMvc.perform(
                    delete("/api/v1/reservations/$reservationId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
})
