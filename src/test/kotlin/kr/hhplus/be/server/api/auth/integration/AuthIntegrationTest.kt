package kr.hhplus.be.server.api.auth.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.models.User
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@IntegrationTest
class AuthIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val userJpaRepository: UserJpaRepository,
    private val objectMapper: ObjectMapper,
) : DescribeSpec({

    extension(SpringExtension)

    lateinit var mockMvc: MockMvc

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }

    describe("토큰 발급 API") {
        context("유효한 사용자 ID로 토큰 발급을 요청할 때") {
            it("토큰이 성공적으로 발급되어야 한다") {
                // given
                val userId = 1000L
                val user = User.createWithId(userId)
                userJpaRepository.save(user)
                
                val request = TokenIssueRequest(userId)

                // when & then
                mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.token").isNotEmpty)
                    .andExpect(jsonPath("$.data.queuePosition").isNumber)
                    .andExpect(jsonPath("$.data.status").isString)
            }
        }

        context("존재하지 않는 사용자 ID로 토큰 발급을 요청할 때") {
            it("400 Bad Request가 반환되어야 한다") {
                // given
                val request = TokenIssueRequest(99999L)

                // when & then
                mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("토큰 상태 조회 API") {
        context("유효한 토큰으로 상태를 조회할 때") {
            it("토큰 상태 정보를 성공적으로 반환해야 한다") {
                // given
                val userId = 2000L
                val user = User.createWithId(userId)
                userJpaRepository.save(user)
                
                // 토큰 발급
                val request = TokenIssueRequest(userId)
                val issueResult = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                ).andReturn()
                
                val responseContent = issueResult.response.contentAsString
                val responseJson = objectMapper.readTree(responseContent)
                val token = responseJson.get("data").get("token").asText()

                // when & then
                mockMvc.perform(
                    get("/api/v1/tokens/{token}", token)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").isString)
                    .andExpect(jsonPath("$.data.queuePosition").isNumber)
            }
        }

        context("유효하지 않은 토큰으로 상태를 조회할 때") {
            it("404 Not Found가 반환되어야 한다") {
                // given
                val invalidToken = "invalid-token-12345"

                // when & then
                mockMvc.perform(
                    get("/api/v1/tokens/{token}", invalidToken)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
            }
        }
    }
})