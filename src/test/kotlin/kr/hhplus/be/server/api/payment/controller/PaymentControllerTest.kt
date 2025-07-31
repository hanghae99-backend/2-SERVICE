package kr.hhplus.be.server.api.payment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.api.payment.controller.PaymentController
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.api.payment.usecase.PaymentUseCase
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(PaymentController::class)
class PaymentControllerTest : DescribeSpec({
    
    val paymentUseCase = mockk<PaymentUseCase>()
    val paymentController = PaymentController(paymentUseCase)
    val mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = ObjectMapper()
    
    describe("POST /api/v1/payments") {
        context("유효한 결제 요청이 들어올 때") {
            it("결제를 처리하고 201 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val request = PaymentRequest(userId, reservationId, token)
                val paymentDto = PaymentDto(
                    paymentId = 1L,
                    userId = userId,
                    amount = BigDecimal("50000"),
                    paymentMethod = "CREDIT_CARD",
                    statusCode = "COMPLETED",
                    paidAt = LocalDateTime.now(),
                    reservationList = emptyList()
                )
                
                every { paymentUseCase.processPayment(userId, reservationId, token) } returns paymentDto
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.paymentId").value(1L))
                    .andExpect(jsonPath("$.data.userId").value(userId))
                    .andExpect(jsonPath("$.data.amount").value(50000))
                    .andExpect(jsonPath("$.message").value("결제가 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("잘못된 형식의 요청이 들어올 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val invalidRequest = PaymentRequest(-1, 1, "")
                every { paymentUseCase.processPayment(invalidRequest.userId, invalidRequest.reservationId, invalidRequest.token) } throws IllegalArgumentException("잘못된 요청입니다")
                // when & then
                mockMvc.perform(
                    post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                )
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
        
        context("결제 처리 실패 시") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "valid-token"
                val request = PaymentRequest(userId, reservationId, token)
                
                every { paymentUseCase.processPayment(userId, reservationId, token) } throws
                        PaymentProcessException("잔액이 부족합니다")
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
        
        context("유효하지 않은 토큰으로 결제 요청할 때") {
            it("401 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val reservationId = 1L
                val token = "invalid-token"
                val request = PaymentRequest(userId, reservationId, token)
                
                every { paymentUseCase.processPayment(userId, reservationId, token) } throws
                        RuntimeException("Invalid token")
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isInternalServerError)
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 예약으로 결제 요청할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val reservationId = 999L
                val token = "valid-token"
                val request = PaymentRequest(userId, reservationId, token)
                
                every { paymentUseCase.processPayment(userId, reservationId, token) } throws
                        IllegalArgumentException("예약을 찾을 수 없습니다")
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isBadRequest)
                    .andDo(print())
            }
        }
    }
    
    describe("GET /api/v1/payments/{paymentId}") {
        context("존재하는 결제 ID로 조회할 때") {
            it("결제 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val paymentId = 1L
                val paymentDto = PaymentDto(
                    paymentId = paymentId,
                    userId = 1L,
                    amount = BigDecimal("50000"),
                    paymentMethod = "CREDIT_CARD",
                    statusCode = "COMPLETED",
                    paidAt = LocalDateTime.now(),
                    reservationList = emptyList()
                )
                
                every { paymentUseCase.getPaymentById(paymentId) } returns paymentDto
                
                // when & then
                mockMvc.perform(get("/api/v1/payments/$paymentId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.paymentId").value(paymentId))
                    .andExpect(jsonPath("$.data.amount").value(50000))
                    .andExpect(jsonPath("$.message").value("결제 정보 조회가 완료되었습니다"))
                    .andDo(print())
            }
        }
        
        context("존재하지 않는 결제 ID로 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val paymentId = 999L
                
                every { paymentUseCase.getPaymentById(paymentId) } throws
                        PaymentNotFoundException("결제를 찾을 수 없습니다: $paymentId")
                
                // when & then
                mockMvc.perform(get("/api/v1/payments/$paymentId"))
                    .andExpect(status().isNotFound)
                    .andDo(print())
            }
        }
    }
})
