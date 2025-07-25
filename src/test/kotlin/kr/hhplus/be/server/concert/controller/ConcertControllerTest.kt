package kr.hhplus.be.server.concert.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.concert.dto.ConcertDto
import kr.hhplus.be.server.concert.dto.ConcertScheduleWithInfoDto
import kr.hhplus.be.server.concert.dto.ConcertDetailDto
import kr.hhplus.be.server.concert.dto.ConcertWithScheduleDto
import kr.hhplus.be.server.concert.entity.Concert
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.service.ConcertService
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

@WebMvcTest(ConcertController::class)
class ConcertControllerTest : DescribeSpec({
    
    val concertService = mockk<ConcertService>()
    val concertController = ConcertController(concertService)
    val mockMvc = MockMvcBuilders.standaloneSetup(concertController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = ObjectMapper()
    
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
        
        context("예약 가능한 콘서트가 없을 때") {
            it("빈 목록을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                
                every { concertService.getAvailableConcerts(startDate, endDate) } returns emptyList()
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString())
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(0))
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
        
        context("존재하지 않는 콘서트의 스케줄을 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val concertId = 999L
                
                every { concertService.getSchedulesByConcertId(concertId) } throws
                        ConcertNotFoundException("콘서트를 찾을 수 없습니다: $concertId")
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/$concertId/schedules"))
                    .andExpect(status().isNotFound)
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
        
        context("존재하지 않는 스케줄 ID로 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val scheduleId = 999L
                
                every { concertService.getConcertDetailByScheduleId(scheduleId) } throws
                        ConcertNotFoundException("스케줄을 찾을 수 없습니다: $scheduleId")
                
                // when & then
                mockMvc.perform(get("/api/v1/concerts/schedules/$scheduleId"))
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
})
