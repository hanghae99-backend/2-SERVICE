package kr.hhplus.be.server.concert.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.concert.dto.SeatDto
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(SeatController::class)
class SeatControllerTest : DescribeSpec({
    
    val seatService = mockk<SeatService>()
    val seatController = SeatController(seatService)
    val mockMvc = MockMvcBuilders.standaloneSetup(seatController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = ObjectMapper()
    
    describe("GET /api/v1/concerts/schedules/{scheduleId}/seats") {
        context("존재하는 스케줄의 예약 가능한 좌석을 조회할 때") {
            it("좌석 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val seats = listOf(
                    SeatDto(
                        seatId = 1L,
                        scheduleId = scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        statusCode = "AVAILABLE",
                    ),
                    SeatDto(
                        seatId = 2L,
                        scheduleId = scheduleId,
                        seatNumber = "A2",
                        price = BigDecimal("50000"),
                        statusCode = "AVAILABLE",
                    )
                )
                
                every { seatService.getAvailableSeats(scheduleId) } returns seats
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/schedules/$scheduleId/seats"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].seatNumber").value("A1"))
                    .andExpect(jsonPath("$.data[0].statusCode").value("AVAILABLE"))
                    .andExpect(jsonPath("$.message").value("예약 가능한 좌석 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("예약 가능한 좌석이 없을 때") {
            it("빈 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 1L
                
                every { seatService.getAvailableSeats(scheduleId) } returns emptyList()
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/schedules/$scheduleId/seats"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(0))
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 스케줄의 좌석을 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 999L
                
                every { seatService.getAvailableSeats(scheduleId) } throws
                        ConcertNotFoundException("스케줄을 찾을 수 없습니다: $scheduleId")
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/schedules/$scheduleId/seats"))
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/schedules/{scheduleId}/seats/all") {
        context("존재하는 스케줄의 모든 좌석을 조회할 때") {
            it("모든 좌석 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val seats = listOf(
                    SeatDto(
                        seatId = 1L,
                        scheduleId = scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        statusCode = "AVAILABLE",
                    ),
                    SeatDto(
                        seatId = 2L,
                        scheduleId = scheduleId,
                        seatNumber = "A2",
                        price = BigDecimal("50000"),
                        statusCode = "RESERVED",
                    )
                )
                
                every { seatService.getAllSeats(scheduleId) } returns seats
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/schedules/$scheduleId/seats/all"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.message").value("모든 좌석 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/seats/{seatId}") {
        context("존재하는 좌석 ID로 조회할 때") {
            it("좌석 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val seatId = 1L
                val seat = SeatDto(
                    seatId = seatId,
                    scheduleId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    statusCode = "AVAILABLE",
                )
                
                every { seatService.getSeatById(seatId) } returns seat
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$seatId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.seatId").value(seatId))
                    .andExpect(jsonPath("$.data.seatNumber").value("A1"))
                    .andExpect(jsonPath("$.data.price").value(50000))
                    .andExpect(jsonPath("$.message").value("좌석 정보 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 좌석 ID로 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val seatId = 999L
                
                every { seatService.getSeatById(seatId) } throws
                        SeatNotFoundException("좌석을 찾을 수 없습니다: $seatId")
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$seatId"))
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/seats/{seatId}/availability") {
        context("예약 가능한 좌석의 가용성을 확인할 때") {
            it("가용성 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val seatId = 1L
                
                every { seatService.isSeatAvailable(seatId) } returns true
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$seatId/availability"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(true))
                    .andExpect(jsonPath("$.message").value("좌석 가용성 확인이 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("예약 불가능한 좌석의 가용성을 확인할 때") {
            it("가용성 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val seatId = 1L
                
                every { seatService.isSeatAvailable(seatId) } returns false
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$seatId/availability"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(false))
                    .andExpect(jsonPath("$.message").value("좌석 가용성 확인이 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 좌석의 가용성을 확인할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val seatId = 999L
                
                every { seatService.isSeatAvailable(seatId) } throws
                        SeatNotFoundException("좌석을 찾을 수 없습니다: $seatId")
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$seatId/availability"))
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/schedules/{scheduleId}/seats/search") {
        context("좌석 번호 패턴으로 검색할 때") {
            it("패턴에 맞는 좌석 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val pattern = "A"
                val seats = listOf(
                    SeatDto(
                        seatId = 1L,
                        scheduleId = scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        statusCode = "AVAILABLE",
                    ),
                    SeatDto(
                        seatId = 2L,
                        scheduleId = scheduleId,
                        seatNumber = "A2",
                        price = BigDecimal("50000"),
                        statusCode = "AVAILABLE",
                    )
                )
                
                every { seatService.getSeatsByNumberPattern(scheduleId, pattern) } returns seats
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/schedules/$scheduleId/seats/search")
                        .param("pattern", pattern)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].seatNumber").value("A1"))
                    .andExpect(jsonPath("$.data[1].seatNumber").value("A2"))
                    .andExpect(jsonPath("$.message").value("좌석 검색이 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("패턴에 맞는 좌석이 없을 때") {
            it("빈 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val pattern = "Z"
                
                every { seatService.getSeatsByNumberPattern(scheduleId, pattern) } returns emptyList()
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/schedules/$scheduleId/seats/search")
                        .param("pattern", pattern)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(0))
                    .andDo(print())
            }
        }
        
        context("패턴 파라미터가 없을 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 1L
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/schedules/$scheduleId/seats/search"))
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
    }
})
