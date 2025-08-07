package kr.hhplus.be.server.api.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.api.auth.usecase.TokenIssueUseCase
import kr.hhplus.be.server.api.auth.usecase.TokenQueueStatusUseCase
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.time.LocalDateTime
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print

@WebMvcTest(TokenController::class)
class TokenControllerTest : DescribeSpec({
    
    val tokenIssueUseCase = mockk<TokenIssueUseCase>()
    val tokenQueueStatusUseCase = mockk<TokenQueueStatusUseCase>()
    val tokenController = TokenController(tokenIssueUseCase, tokenQueueStatusUseCase)
    val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
    
    val mockMvc = MockMvcBuilders.standaloneSetup(tokenController)
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator)
        .setMessageConverters(MappingJackson2HttpMessageConverter())
        .build()
    val objectMapper = ObjectMapper()
    
    describe("POST /api/v1/tokens") {
        context("유효한 토큰 발급 요청이 들어올 때") {
            it("토큰을 발급하고 201 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val request = TokenIssueRequest(userId)
                val tokenIssueDetail = TokenIssueDetail.fromTokenWithDetails(
                    token = "test-token",
                    status = "WAITING",
                    message = "대기열에 등록되었습니다",
                    userId = userId,
                    queuePosition = 5,
                    estimatedWaitingTime = 10,
                    issuedAt = LocalDateTime.now()
                )
                
                every { tokenIssueUseCase.execute(userId) } returns tokenIssueDetail
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("대기열 토큰이 성공적으로 발급되었습니다"))
                    .andExpect(jsonPath("$.data.token").value("test-token"))
                    .andExpect(jsonPath("$.data.status").value("WAITING"))
                    .andExpect(jsonPath("$.data.message").value("대기열에 등록되었습니다"))
                    .andExpect(jsonPath("$.data.userId").value(userId))
                    .andExpect(jsonPath("$.data.queuePosition").value(5))
                    .andExpect(jsonPath("$.data.estimatedWaitingTime").value(10))
                
                verify { tokenIssueUseCase.execute(userId) }
            }
        }
        
        context("존재하지 않는 사용자가 토큰 발급 요청할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val userId = 999L
                val request = TokenIssueRequest(userId)
                
                every { tokenIssueUseCase.execute(userId) } throws 
                    UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isNotFound)
                
                verify { tokenIssueUseCase.execute(userId) }
            }
        }

        context("잘못된 형식의 요청이 들어올 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val invalidRequest = """{"userId": -1}"""

                // 음수 userId에 대해 예외 발생하도록 설정
                every { tokenIssueUseCase.execute(-1) } throws
                        IllegalArgumentException("유효하지 않은 사용자 ID입니다")

                // when & then
                mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest)
                )
                    .andExpect(status().isBadRequest)
                    .andDo(print())

                verify { tokenIssueUseCase.execute(-1) }
            }
        }
    }
    
    describe("GET /api/v1/tokens/{token}") {
        context("존재하는 토큰의 상태를 조회할 때") {
            it("토큰 상태를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val token = "test-token"
                val tokenQueueDetail = TokenQueueDetail.fromTokenWithQueue(
                    token = token,
                    status = "WAITING",
                    message = "대기 중입니다",
                    queuePosition = 3,
                    estimatedWaitingTime = 6
                )
                
                every { tokenQueueStatusUseCase.execute(token) } returns tokenQueueDetail
                
                // when & then
                mockMvc.perform(get("/api/v1/tokens/$token"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("토큰 대기열 상태 조회가 완료되었습니다"))
                    .andExpect(jsonPath("$.data.token").value(token))
                    .andExpect(jsonPath("$.data.status").value("WAITING"))
                    .andExpect(jsonPath("$.data.message").value("대기 중입니다"))
                    .andExpect(jsonPath("$.data.queuePosition").value(3))
                    .andExpect(jsonPath("$.data.estimatedWaitingTime").value(6))
                
                verify { tokenQueueStatusUseCase.execute(token) }
            }
        }
        
        context("활성화된 토큰의 상태를 조회할 때") {
            it("활성화 상태를 반환해야 한다") {
                // given
                val token = "active-token"
                val tokenQueueDetail = TokenQueueDetail.fromTokenWithQueue(
                    token = token,
                    status = "ACTIVE",
                    message = "서비스 이용 가능합니다",
                    queuePosition = null,
                    estimatedWaitingTime = null
                )
                
                every { tokenQueueStatusUseCase.execute(token) } returns tokenQueueDetail
                
                // when & then
                mockMvc.perform(get("/api/v1/tokens/$token"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("토큰 대기열 상태 조회가 완료되었습니다"))
                    .andExpect(jsonPath("$.data.token").value(token))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.message").value("서비스 이용 가능합니다"))
                    .andExpect(jsonPath("$.data.queuePosition").doesNotExist())
                    .andExpect(jsonPath("$.data.estimatedWaitingTime").doesNotExist())
                
                verify { tokenQueueStatusUseCase.execute(token) }
            }
        }
        
        context("존재하지 않는 토큰의 상태를 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val token = "non-existent-token"
                
                every { tokenQueueStatusUseCase.execute(token) } throws 
                    TokenNotFoundException("토큰을 찾을 수 없습니다.")
                
                // when & then
                mockMvc.perform(get("/api/v1/tokens/$token"))
                    .andExpect(status().isNotFound)
                
                verify { tokenQueueStatusUseCase.execute(token) }
            }
        }
    }
    

})
