package kr.hhplus.be.server.concert.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.api.concert.controller.SeatController
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal

@WebMvcTest(SeatController::class)
class SeatControllerTest : DescribeSpec({
    
    val seatService = mockk<SeatService>()
    val seatController = SeatController(seatService)
    val mockMvc = MockMvcBuilders.standaloneSetup(seatController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = ObjectMapper()
    
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
                    statusCode = "AVAILABLE"
                )
                
                every { seatService.getSeatById(seatId) } returns seat
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$seatId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.seatId").value(seatId))
                    .andExpect(jsonPath("$.data.seatNumber").value("A1"))
                    .andExpect(jsonPath("$.data.price").value(50000))
                    .andExpect(jsonPath("$.data.statusCode").value("AVAILABLE"))
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
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.message").value("좌석을 찾을 수 없습니다: $seatId"))
                    .andDo(print())
            }
        }
        
        context("잘못된 좌석 ID로 조회할 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val invalidSeatId = -1L
                
                every { seatService.getSeatById(invalidSeatId) } throws 
                    IllegalArgumentException("좌석 ID는 양수여야 합니다")
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$invalidSeatId"))
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
        
        context("좌석 ID가 0일 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val zeroSeatId = 0L
                
                every { seatService.getSeatById(zeroSeatId) } throws 
                    IllegalArgumentException("좌석 ID는 양수여야 합니다")
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$zeroSeatId"))
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/seats/{seatId}/availability") {
        context("예약 가능한 좌석의 가용성을 확인할 때") {
            it("true를 반환하고 200 상태코드를 반환해야 한다") {
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
            it("false를 반환하고 200 상태코드를 반환해야 한다") {
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
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.message").value("좌석을 찾을 수 없습니다: $seatId"))
                    .andDo(print())
            }
        }
        
        context("잘못된 좌석 ID로 가용성을 확인할 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val invalidSeatId = -1L
                
                every { seatService.isSeatAvailable(invalidSeatId) } throws 
                    IllegalArgumentException("좌석 ID는 양수여야 합니다")
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$invalidSeatId/availability"))
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
        
        context("서비스에서 예외가 발생할 때") {
            it("적절한 에러 응답을 반환해야 한다") {
                // given
                val seatId = 1L
                
                every { seatService.isSeatAvailable(seatId) } throws 
                    RuntimeException("서비스 오류가 발생했습니다")
                
                // when & then
                mockMvc.perform(get("/api/v1/seats/$seatId/availability"))
                    .andExpect(status().isInternalServerError)
                    .andDo(print())
            }
        }
    }
})