package kr.hhplus.be.server.api.concert.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.api.concert.dto.*
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.LocalDate

@WebMvcTest(ConcertController::class)
class ConcertControllerTest : DescribeSpec({
    
    val concertService = mockk<ConcertService>()
    val seatService = mockk<SeatService>()
    val concertController = ConcertController(concertService, seatService)
    val mockMvc = MockMvcBuilders.standaloneSetup(concertController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = ObjectMapper().apply {
        findAndRegisterModules()
    }
    
    describe("GET /api/v1/concerts") {
        context("예약 가능한 콘서트 목록을 조회할 때") {
            it("콘서트 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                val concerts = listOf(
                    ConcertScheduleWithInfoDto(
                        scheduleId = 1L,
                        concertId = 1L,
                        title = "콘서트 1",
                        artist = "아티스트 1",
                        venue = "장소 1",
                        concertDate = LocalDate.now(),
                        totalSeats = 100,
                        availableSeats = 50
                    )
                )
                
                every { concertService.getAvailableConcerts(startDate, endDate) } returns concerts
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString())
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.message").value("예약 가능한 콘서트 목록 조회 완료"))
                    .andDo(print())
            }
        }
        
        context("날짜 파라미터 없이 조회할 때") {
            it("기본값으로 3개월 기간의 콘서트 목록을 반환해야 한다") {
                // given
                val concerts = listOf(
                    ConcertScheduleWithInfoDto(
                        scheduleId = 1L,
                        concertId = 1L,
                        title = "콘서트 1",
                        artist = "아티스트 1",
                        venue = "장소 1",
                        concertDate = LocalDate.now(),
                        totalSeats = 100,
                        availableSeats = 50
                    )
                )
                
                every { concertService.getAvailableConcerts(any(), any()) } returns concerts
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/{concertId}") {
        context("존재하는 콘서트 ID로 조회할 때") {
            it("콘서트 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = ConcertDto(
                    concertId = concertId,
                    title = "콘서트 1",
                    artist = "아티스트 1"
                )
                
                every { concertService.getConcertById(concertId) } returns concert
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/$concertId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.concertId").value(concertId))
                    .andExpect(jsonPath("$.data.title").value("콘서트 1"))
                    .andExpect(jsonPath("$.message").value("콘서트 정보 조회 완료"))
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 콘서트 ID로 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val concertId = 999L
                
                every { concertService.getConcertById(concertId) } throws 
                    ConcertNotFoundException("콘서트를 찾을 수 없습니다: $concertId")
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/$concertId"))
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/{concertId}/schedules") {
        context("콘서트 ID로 스케줄 목록을 조회할 때") {
            it("스케줄 목록을 반환해야 한다") {
                // given
                val concertId = 1L
                val schedules = listOf(
                    mockk<ConcertWithScheduleDto>(relaxed = true)
                )
                
                every { concertService.getSchedulesByConcertId(concertId) } returns schedules
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/$concertId/schedules"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.message").value("콘서트 스케줄 조회 완료"))
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/{concertId}/schedules/{scheduleId}") {
        context("콘서트와 스케줄 ID로 상세 정보를 조회할 때") {
            it("콘서트 상세 정보를 반환해야 한다") {
                // given
                val concertId = 1L
                val scheduleId = 1L
                val detail = ConcertDetailDto(
                    concert = ConcertDto(concertId, "테스트 콘서트", "테스트 아티스트"),
                    schedule = ConcertScheduleDto(
                        scheduleId = scheduleId,
                        concertId = concertId,
                        venue = "테스트 장소",
                        concertDate = LocalDate.now(),
                        totalSeats = 100,
                        availableSeats = 50
                    ),
                    seats = emptyList()
                )
                
                every { concertService.getConcertDetailByScheduleId(scheduleId) } returns detail
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/$concertId/schedules/$scheduleId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("콘서트 스케줄 상세 조회 완료"))
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/{concertId}/schedules/{scheduleId}/seats") {
        context("예약 가능한 좌석만 조회할 때 (기본값)") {
            it("예약 가능한 좌석 목록을 반환해야 한다") {
                // given
                val concertId = 1L
                val scheduleId = 1L
                val seats = listOf(
                    SeatDto(
                        seatId = 1L,
                        scheduleId = scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        statusCode = "AVAILABLE"
                    )
                )
                
                every { seatService.getAvailableSeats(scheduleId) } returns seats
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/$concertId/schedules/$scheduleId/seats"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].statusCode").value("AVAILABLE"))
                    .andExpect(jsonPath("$.message").value("좌석 조회 완료"))
                    .andDo(print())
            }
        }
        
        context("모든 좌석 조회를 요청할 때") {
            it("모든 좌석 목록을 반환해야 한다") {
                // given
                val concertId = 1L
                val scheduleId = 1L
                val seats = listOf(
                    SeatDto(
                        seatId = 1L,
                        scheduleId = scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        statusCode = "AVAILABLE"
                    ),
                    SeatDto(
                        seatId = 2L,
                        scheduleId = scheduleId,
                        seatNumber = "A2",
                        price = BigDecimal("50000"),
                        statusCode = "RESERVED"
                    )
                )
                
                every { seatService.getAllSeats(scheduleId) } returns seats
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/$concertId/schedules/$scheduleId/seats")
                        .param("availableOnly", "false")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.message").value("좌석 조회 완료"))
                    .andDo(print())
            }
        }
    }
})
