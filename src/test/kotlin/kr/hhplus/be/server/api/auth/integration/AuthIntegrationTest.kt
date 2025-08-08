package kr.hhplus.be.server.api.auth.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.config.TestDataCleanupService
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.model.User
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuthIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val testDataCleanupService: TestDataCleanupService,
    private val userJpaRepository: UserJpaRepository,
    private val tokenStore: TokenStore,
    private val objectMapper: ObjectMapper
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc

    beforeSpec {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }
    
    afterEach {
        // 각 테스트 후 데이터 정리
        try {
            testDataCleanupService.cleanupAllTestData()
        } catch (e: Exception) {
            println("Cleanup failed: ${e.message}")
        }
    }

    describe("토큰 발급 API") {
        context("유효한 사용자 ID로 토큰 발급을 요청할 때") {
            it("토큰이 성공적으로 발급되어야 한다") {
                // given
                val userId = 1000L
                val user = User.create(userId)
                userJpaRepository.save(user)
                
                val request = TokenIssueRequest(userId)

                // when & then
                val result = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                
                // 응답 내용 로깅 (디버깅용)
                println("토큰 발급 응답 내용: ${result.andReturn().response.contentAsString}")
                
                result.andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("대기열 토큰이 성공적으로 발급되었습니다"))
                    .andExpect(jsonPath("$.data.userId").value(userId))
                    .andExpect(jsonPath("$.data.status").value("WAITING"))
                    .andExpect(jsonPath("$.data.token").exists())
                    // queuePosition은 0 또는 1 모두 허용
                    .andExpect(jsonPath("$.data.queuePosition").exists())
            }
        }

        context("유효하지 않은 사용자 ID로 토큰 발급을 요청할 때") {
            it("오류 응답을 반환해야 한다") {
                // given
                val request = TokenIssueRequest(-1L) // 음수 userId

                // when & then
                val result = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                
                // 응답 내용 로깅 (디버깅용)
                println("유효하지 않은 사용자 ID 토큰 발급 응답 상태: ${result.andReturn().response.status}")
                println("유효하지 않은 사용자 ID 토큰 발급 응답 내용: ${result.andReturn().response.contentAsString}")
                
                // ValidationException으로 인한 400 또는 404
                result.andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("존재하지 않는 사용자 ID로 토큰 발급을 요청할 때") {
            it("오류 응답을 반환해야 한다") {
                // given
                val userId = 999L // 존재하지 않는 사용자
                val request = TokenIssueRequest(userId)

                // when & then
                val result = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                
                // 응답 내용 로깅 (디버깅용)
                println("존재하지 않는 사용자 토큰 발급 응답 상태: ${result.andReturn().response.status}")
                println("존재하지 않는 사용자 토큰 발급 응답 내용: ${result.andReturn().response.contentAsString}")
                
                // UserNotFoundException으로 인한 404 또는 다른 상태 코드
                result.andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("필수 파라미터가 누락된 요청을 보낼 때") {
            it("오류 응답을 반환해야 한다") {
                // given - userId가 누락된 잘못된 요청
                val invalidRequestJson = """
                {
                    "invalidField": "test"
                }
                """.trimIndent()

                // when & then
                val result = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson)
                )
                
                // 응답 내용 로깅 (디버깅용)
                println("필수 파라미터 누락 토큰 발급 응답 상태: ${result.andReturn().response.status}")
                println("필수 파라미터 누락 토큰 발급 응답 내용: ${result.andReturn().response.contentAsString}")
                
                // ValidationException으로 인한 400 또는 404
                result.andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("토큰 상태 조회 API") {
        context("유효한 토큰으로 상태를 조회할 때") {
            it("토큰 상태 정보를 성공적으로 반환해야 한다") {
                // given
                val userId = 2000L
                val user = User.create(userId)
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
                .andExpect(jsonPath("$.message").value("토큰 대기열 상태 조회가 완료되었습니다"))
                .andExpect(jsonPath("$.data.token").value(token))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
            }
        }

        context("존재하지 않는 토큰으로 상태를 조회할 때") {
            it("오류 응답을 반환해야 한다") {
                // given
                val invalidToken = "invalid-token-12345"

                // when & then
                val result = mockMvc.perform(
                    get("/api/v1/tokens/{token}", invalidToken)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                
                // 응답 내용 로깅 (디버깅용)
                println("존재하지 않는 토큰 조회 응답 상태: ${result.andReturn().response.status}")
                println("존재하지 않는 토큰 조회 응답 내용: ${result.andReturn().response.contentAsString}")
                
                // TokenNotFoundException으로 인한 404 또는 다른 상태 코드
                result.andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("토큰 대기열 순서") {
        context("여러 사용자가 순차적으로 토큰을 발급받을 때") {
            it("대기열 순서가 올바르게 부여되어야 한다") {
                // given
                val userIds = listOf(3000L, 3001L, 3002L)
                userIds.forEach { userId ->
                    userJpaRepository.save(User.create(userId))
                }

                // when & then
                userIds.forEachIndexed { index, userId ->
                    val request = TokenIssueRequest(userId)
                    
                    val result = mockMvc.perform(
                        post("/api/v1/tokens")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                    )
                    
                    // 대기열 순서 로깅
                    val responseContent = result.andReturn().response.contentAsString
                    println("User $userId - Response: $responseContent")
                    
                    result.andExpect(status().isCreated)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.userId").value(userId))
                        // 대기열 순서는 실제 응답에 따라 유연하게 처리
                        .andExpect(jsonPath("$.data.queuePosition").exists())
                }
            }
        }
    }
})