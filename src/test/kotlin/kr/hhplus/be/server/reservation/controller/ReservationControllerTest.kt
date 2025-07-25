package kr.hhplus.be.server.reservation.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.reservation.entity.Reservation
import kr.hhplus.be.server.reservation.exception.ReservationNotFoundException
import kr.hhplus.be.server.reservation.service.ReservationService
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal

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
                val reservation = Reservation(
                    reservationId = 1L,
                    userId = userId,
                    concertId = concertId,
                    seatId = seatId,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    statusCode = "TEMPORARY"
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
                    .andExpect(jsonPath("$.data.userId").value(userId))
                    .andExpect(jsonPath("$.data.concertId").value(concertId))
                    .andExpect(jsonPath("$.data.seatId").value(seatId))
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
                val reservation = Reservation(
                    reservationId = 1L,
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    statusCode = "TEMPORARY"
                )
                
                every { reservationService.confirmReservation(reservationId, paymentId) } returns reservation
                
                // when & then
                mockMvc.perform(
                    put("/api/v1/reservations/$reservationId/confirm")
                        .param("paymentId", paymentId.toString())
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
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
                val reservation = Reservation(
                    reservationId = reservationId,
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    statusCode = "TEMPORARY"
                )
                
                every { reservationService.getReservationById(reservationId) } returns reservation
                
                // when & then
                mockMvc.perform(get("/api/v1/reservations/$reservationId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.reservationId").value(reservationId))
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
    
    describe("DELETE /api/v1/reservations/{reservationId}") {
        context("유효한 예약 취소 요청이 들어올 때") {
            it("예약을 취소하고 200 상태코드를 반환해야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val reason = "사용자 요청"
                val request = ReservationCancelRequest(userId, reason)
                val reservation = Reservation(
                    reservationId = 1L,
                    userId = userId,
                    concertId = 1L,
                    seatId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    statusCode = "TEMPORARY"
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
    }
})
