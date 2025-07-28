package kr.hhplus.be.server.concert.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.api.concert.controller.ConcertController
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.concert.dto.*
import kr.hhplus.be.server.concert.dto.request.SearchConcertRequest
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
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
                    .andExpect(jsonPath("$.message").value("예약 가능한 콘서트 목록 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("날짜 파라미터 없이 조회할 때") {
            it("기본값으로 3개월 기간의 콘서트 목록을 반환해야 한다") {
                // given
                val defaultStart = LocalDate.now()
                val defaultEnd = LocalDate.now().plusMonths(3)
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
                
                every { concertService.getAvailableConcerts(defaultStart, defaultEnd) } returns concerts
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andDo(print())
            }
        }
    }
    
    describe("POST /api/v1/concerts/search") {
        context("콘서트 검색 요청을 보낼 때") {
            it("검색 결과를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val request = SearchConcertRequest(
                    startDate = LocalDate.now(),
                    endDate = LocalDate.now().plusMonths(1)
                )
                val concerts = listOf(
                    ConcertScheduleWithInfoDto(
                        scheduleId = 1L,
                        concertId = 1L,
                        title = "검색된 콘서트",
                        artist = "아티스트",
                        venue = "장소",
                        concertDate = LocalDate.now(),
                        totalSeats = 100,
                        availableSeats = 50
                    )
                )
                
                every { concertService.getAvailableConcerts(request.startDate!!, request.endDate!!) } returns concerts
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/concerts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.message").value("콘서트 검색이 완료되었습니다"))
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
                    .andExpect(jsonPath("$.message").value("콘서트 정보 조회가 완료되었습니다"))
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
        
        context("잘못된 콘서트 ID로 조회할 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val invalidConcertId = -1L
                
                every { concertService.getConcertById(invalidConcertId) } throws 
                    IllegalArgumentException("콘서트 ID는 양수여야 합니다")
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/$invalidConcertId"))
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/{concertId}/schedules") {
        context("존재하는 콘서트의 스케줄을 조회할 때") {
            it("스케줄 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = mockk<Concert>(relaxed = true)
                val schedule = mockk<ConcertSchedule>(relaxed = true)
                val schedules = listOf(
                    ConcertWithScheduleDto(
                        concert = concert,
                        schedule = schedule
                    )
                )
                
                every { concertService.getSchedulesByConcertId(concertId) } returns schedules
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/$concertId/schedules"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.message").value("콘서트 스케줄 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/schedules/{scheduleId}") {
        context("존재하는 스케줄 ID로 상세 정보를 조회할 때") {
            it("상세 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val concert = mockk<Concert>(relaxed = true)
                val schedule = mockk<ConcertSchedule>(relaxed = true)
                val concertDetail = ConcertDetailDto.from(concert, schedule, emptyList())
                
                every { concertService.getConcertDetailByScheduleId(scheduleId) } returns concertDetail
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/schedules/$scheduleId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("콘서트 상세 정보 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/concerts/schedules/{scheduleId}/seats") {
        context("예약 가능한 좌석만 조회할 때 (기본값)") {
            it("예약 가능한 좌석 목록을 반환해야 한다") {
                // given
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
                mockMvc.perform(get("/api/v1/concerts/schedules/$scheduleId/seats"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].statusCode").value("AVAILABLE"))
                    .andExpect(jsonPath("$.message").value("좌석 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("모든 좌석 조회를 요청할 때") {
            it("모든 좌석 목록을 반환해야 한다") {
                // given
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
                    get("/api/v1/concerts/schedules/$scheduleId/seats")
                        .param("availableOnly", "false")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.message").value("좌석 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("좌석 번호 패턴으로 검색할 때") {
            it("패턴에 맞는 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val pattern = "A"
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
                        statusCode = "AVAILABLE"
                    )
                )
                
                every { seatService.getSeatsByNumberPattern(scheduleId, pattern) } returns seats
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/schedules/$scheduleId/seats")
                        .param("seatNumberPattern", pattern)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].seatNumber").value("A1"))
                    .andExpect(jsonPath("$.data[1].seatNumber").value("A2"))
                    .andExpect(jsonPath("$.message").value("좌석 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("패턴 검색과 예약 가능 필터를 함께 사용할 때") {
            it("조건에 맞는 좌석만 반환해야 한다") {
                // given
                val scheduleId = 1L
                val pattern = "A"
                val availableSeats = listOf(
                    SeatDto(
                        seatId = 1L,
                        scheduleId = scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        statusCode = "AVAILABLE"
                    )
                )
                
                every { seatService.getSeatsByNumberPattern(scheduleId, pattern) } returns availableSeats
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/schedules/$scheduleId/seats")
                        .param("seatNumberPattern", pattern)
                        .param("availableOnly", "true")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].statusCode").value("AVAILABLE"))
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
})