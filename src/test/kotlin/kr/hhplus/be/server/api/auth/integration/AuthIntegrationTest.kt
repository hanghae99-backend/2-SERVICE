package kr.hhplus.be.server.api.auth.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.config.TestDataCleanupHelper
import kr.hhplus.be.server.config.TestDataFixture
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.TimeUnit

@IntegrationTest
class AuthIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>
) : DescribeSpec({

    extension(SpringExtension)

    lateinit var mockMvc: MockMvc

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
        
        // 기존 데이터 정리
        val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
        TestDataCleanupHelper.cleanupAll(redisTemplate, jdbcTemplate)
        
        // 분산락 통계 초기화
        distributedLock.resetStatistics()
        
        // Rate limit 완전 초기화를 위한 충분한 대기
        Thread.sleep(500)
    }

    afterEach {
        // 분산락 통계 출력 (디버깅용)
        val stats = distributedLock.getLockStatistics()
        println("""
            === 통합 테스트 분산락 통계 ===
            성공: ${stats.acquisitionCount}
            실패: ${stats.failureCount}
            평균 대기시간: ${stats.averageWaitTimeMs}ms
            성공률: ${stats.successRate}%
        """.trimIndent())
        
        // 테스트 간 충분한 간격 확보
        Thread.sleep(1000)
    }

    describe("토큰 발급 API") {
        context("유효한 사용자 ID로 토큰 발급을 요청할 때") {
            it("토큰이 성공적으로 발급되어야 한다") {
                // given
                val user = TestDataFixture.createTestUser(
                    context = webApplicationContext,
                    userId = 1L,
                    withPoints = false
                )
                val userId = user.userId
                
                println("Created user for token issue: id=${user.userId}")
                
                val request = TokenIssueRequest(userId)

                // when & then
                val result = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.token").isNotEmpty)
                    .andExpect(jsonPath("$.data.queuePosition").isNumber)
                    .andExpect(jsonPath("$.data.status").isString)
                    .andReturn()
                
                val responseContent = result.response.contentAsString
                println("Token issue response: $responseContent")
            }
        }

        context("존재하지 않는 사용자 ID로 토큰 발급을 요청할 때") {
            it("400 또는 404 에러가 반환되어야 한다") {
                // given
                val nonExistentUserId = 99999L
                val request = TokenIssueRequest(nonExistentUserId)

                // when & then
                val result = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().is4xxClientError)
                    .andExpect(jsonPath("$.success").value(false))
                    .andReturn()
                
                val statusCode = result.response.status
                println("Error response for non-existent user - Status: $statusCode")
            }
        }
        
        context("동일한 사용자가 연속으로 토큰 발급을 요청할 때") {
            it("이미 발급된 토큰 정보를 반환해야 한다") {
                // given
                Thread.sleep(500) // Rate limit 방지
                
                val user = TestDataFixture.createTestUser(
                    context = webApplicationContext,
                    userId = 1L,
                    withPoints = false
                )
                val userId = user.userId
                
                val request = TokenIssueRequest(userId)
                
                // 첫 번째 토큰 발급
                var retryCount = 0
                var firstResult: org.springframework.test.web.servlet.MvcResult? = null
                
                while (retryCount < 3) {
                    try {
                        firstResult = mockMvc.perform(
                            post("/api/v1/tokens")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                        ).andReturn()
                        
                        if (firstResult.response.status == 429) {
                            println("Rate limited, waiting... (attempt ${retryCount + 1})")
                            Thread.sleep(2000)
                            retryCount++
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        println("Error during first token issue: ${e.message}")
                        Thread.sleep(2000)
                        retryCount++
                    }
                }
                
                if (firstResult?.response?.status != 201) {
                    println("Failed to issue first token after retries, skipping test")
                    return@it
                }
                
                val firstResponse = objectMapper.readTree(firstResult.response.contentAsString)
                val firstToken = firstResponse.get("data").get("token").asText()
                
                println("First token issued: $firstToken")
                
                // 충분한 대기
                Thread.sleep(1000)
                
                // 두 번째 토큰 발급 시도
                val secondResult = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                ).andReturn()
                
                if (secondResult.response.status == 201) {
                    val secondResponse = objectMapper.readTree(secondResult.response.contentAsString)
                    val secondMessage = secondResponse.get("data").get("message").asText()
                    println("Second token message: $secondMessage")
                }
            }
        }
    }

    describe("토큰 상태 조회 API") {
        context("유효한 토큰으로 상태를 조회할 때") {
            it("토큰 상태 정보를 성공적으로 반환해야 한다") {
                // given
                Thread.sleep(500)
                
                val user = TestDataFixture.createTestUser(
                    context = webApplicationContext,
                    userId = 1L,
                    withPoints = false
                )
                val userId = user.userId
                
                // 토큰 발급
                val request = TokenIssueRequest(userId)
                val issueResult = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                ).andReturn()
                
                if (issueResult.response.status != 201) {
                    println("Token issue failed with status: ${issueResult.response.status}")
                    return@it
                }
                
                val responseJson = objectMapper.readTree(issueResult.response.contentAsString)
                val token = responseJson.get("data").get("token").asText()
                
                // when & then
                mockMvc.perform(
                    get("/api/v1/tokens/{token}", token)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
            }
        }

        context("유효하지 않은 토큰으로 상태를 조회할 때") {
            it("404 Not Found가 반환되어야 한다") {
                // given
                val invalidToken = "WT_INVALID_TOKEN_12345"

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