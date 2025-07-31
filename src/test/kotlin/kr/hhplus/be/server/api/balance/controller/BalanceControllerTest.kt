package kr.hhplus.be.server.api.balance.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.balance.controller.BalanceController
import kr.hhplus.be.server.api.balance.dto.request.ChargeBalanceRequest
import kr.hhplus.be.server.api.balance.usecase.BalanceUseCase
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print

@WebMvcTest(BalanceController::class)
class BalanceControllerTest : DescribeSpec({
    
    val balanceUseCase = mockk<BalanceUseCase>()
    val balanceController = BalanceController(balanceUseCase)
    val mockMvc = MockMvcBuilders.standaloneSetup(balanceController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = ObjectMapper()
    
    describe("POST /api/v1/balance") {
        context("유효한 충전 요청이 들어올 때") {
            it("포인트를 충전하고 200 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val amount = BigDecimal("10000")
                val request = ChargeBalanceRequest(userId, amount)
                val point = Point.create(userId, BigDecimal("15000"))
                
                every { balanceUseCase.chargeBalance(userId, amount) } returns point
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.chargedAmount").value(amount))
                    .andExpect(jsonPath("$.message").value("잔액 충전이 완료되었습니다"))
                
                verify { balanceUseCase.chargeBalance(userId, amount) }
            }
        }
        
        context("존재하지 않는 사용자가 충전 요청할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val userId = 999L
                val amount = BigDecimal("10000")
                val request = ChargeBalanceRequest(userId, amount)
                
                every { balanceUseCase.chargeBalance(userId, amount) } throws 
                    UserNotFoundException("존재하지 않는 사용자입니다: $userId")
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isNotFound)
                
                verify { balanceUseCase.chargeBalance(userId, amount) }
            }
        }
        
        context("잘못된 충전 금액으로 요청할 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val amount = BigDecimal("500") // 최소 금액 미만
                val request = ChargeBalanceRequest(userId, amount)
                
                every { balanceUseCase.chargeBalance(userId, amount) } throws 
                    InvalidPointAmountException("최소 충전 금액은 1000원입니다: $amount")
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isBadRequest)
                
                verify { balanceUseCase.chargeBalance(userId, amount) }
            }
        }
    }
    
    describe("GET /api/v1/balance/{userId}") {
        context("존재하는 사용자의 잔액을 조회할 때") {
            it("잔액 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val point = Point.create(userId, BigDecimal("50000"))
                
                every { balanceUseCase.getBalance(userId) } returns point
                
                // when & then
                mockMvc.perform(get("/api/v1/balance/$userId"))
                    .andDo { print() }
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(userId))
                    .andExpect(jsonPath("$.data.balance").value(50000))
                    .andExpect(jsonPath("$.message").value("잔액 조회가 완료되었습니다"))
                
                verify { balanceUseCase.getBalance(userId) }
            }
        }
        
        context("존재하지 않는 사용자의 잔액을 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val userId = 999L
                
                every { balanceUseCase.getBalance(userId) } throws 
                    UserNotFoundException("존재하지 않는 사용자입니다: $userId")
                
                // when & then
                mockMvc.perform(get("/api/v1/balance/$userId"))
                    .andExpect(status().isNotFound)
                
                verify { balanceUseCase.getBalance(userId) }
            }
        }
    }
    
    describe("GET /api/v1/balance/history/{userId}") {
        context("존재하는 사용자의 포인트 이력을 조회할 때") {
            it("포인트 이력을 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val chargeType = PointHistoryType("CHARGE", "충전", "포인트 충전", true, LocalDateTime.now())
                val useType = PointHistoryType("USE", "사용", "포인트 사용", true, LocalDateTime.now())
                val histories = listOf(
                    PointHistory.charge(userId, BigDecimal("10000"), chargeType, "포인트 충전"),
                    PointHistory.use(userId, BigDecimal("5000"), useType, "포인트 사용")
                )
                
                every { balanceUseCase.getPointHistory(userId) } returns histories
                
                // when & then
                mockMvc.perform(get("/api/v1/balance/history/$userId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.message").value("포인트 이력 조회가 완료되었습니다"))
                
                verify { balanceUseCase.getPointHistory(userId) }
            }
        }
        
        context("존재하지 않는 사용자의 포인트 이력을 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val userId = 999L
                
                every { balanceUseCase.getPointHistory(userId) } throws 
                    UserNotFoundException("존재하지 않는 사용자입니다: $userId")
                
                // when & then
                mockMvc.perform(get("/api/v1/balance/history/$userId"))
                    .andExpect(status().isNotFound)
                
                verify { balanceUseCase.getPointHistory(userId) }
            }
        }
        
        context("음수 사용자 ID로 조회할 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val invalidUserId = -1L

                every { balanceUseCase.getPointHistory(invalidUserId) } throws
                        IllegalArgumentException("유효하지 않은 사용자 ID입니다")

                // when & then
                mockMvc.perform(get("/api/v1/balance/history/$invalidUserId"))
                    .andExpect(status().isBadRequest)
                
                verify { balanceUseCase.getPointHistory(invalidUserId) }
            }
        }
    }
})
