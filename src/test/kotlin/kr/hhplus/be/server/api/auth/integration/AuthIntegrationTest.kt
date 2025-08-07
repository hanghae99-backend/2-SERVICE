package kr.hhplus.be.server.api.auth.integration

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.model.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var tokenStore: TokenStore

    @Autowired
    private lateinit var tokenFactory: TokenFactory

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        userJpaRepository.deleteAll()
    }

    @Test
    @DisplayName("토큰 발급 성공 테스트")
    fun issueToken_Success() {
        // given
        val userId = 1000L
        val user = User.create(userId)
        userJpaRepository.save(user)
        
        val request = TokenIssueRequest(userId)

        // when & then
        mockMvc.perform(
            post("/api/v1/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andDo(print())
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("대기열 토큰이 성공적으로 발급되었습니다"))
        .andExpect(jsonPath("$.data.userId").value(userId))
        .andExpect(jsonPath("$.data.status").value("WAITING"))
        .andExpect(jsonPath("$.data.token").exists())
        .andExpect(jsonPath("$.data.queuePosition").value(1))
    }

    @Test
    @DisplayName("토큰 발급 실패 테스트 - 유효하지 않은 사용자 ID")
    fun issueToken_Fail_InvalidUserId() {
        // given
        val request = TokenIssueRequest(-1L) // 음수 userId

        // when & then
        mockMvc.perform(
            post("/api/v1/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andDo(print())
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("토큰 발급 실패 테스트 - 존재하지 않는 사용자")
    fun issueToken_Fail_UserNotFound() {
        // given
        val userId = 999L // 존재하지 않는 사용자
        val request = TokenIssueRequest(userId)

        // when & then
        mockMvc.perform(
            post("/api/v1/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andDo(print())
        .andExpect(status().isNotFound)
        .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("토큰 상태 조회 성공 테스트")
    fun getTokenStatus_Success() {
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
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("토큰 대기열 상태 조회가 완료되었습니다"))
        .andExpect(jsonPath("$.data.token").value(token))
        .andExpect(jsonPath("$.data.status").value("WAITING"))
    }

    @Test
    @DisplayName("토큰 상태 조회 실패 테스트 - 존재하지 않는 토큰")
    fun getTokenStatus_Fail_TokenNotFound() {
        // given
        val invalidToken = "invalid-token-12345"

        // when & then
        mockMvc.perform(
            get("/api/v1/tokens/{token}", invalidToken)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isNotFound)
        .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("동시 토큰 발급 테스트")
    fun concurrentTokenIssue_Test() {
        // given
        val userCount = 3
        val userIds = (3000L until 3000L + userCount).toList()
        
        // 사용자들 미리 생성
        userIds.forEach { userId ->
            userJpaRepository.save(User.create(userId))
        }
        
        val executor = Executors.newFixedThreadPool(userCount)
        val results = mutableListOf<CompletableFuture<String>>()
        val successCount = AtomicInteger(0)

        // when - 동시 요청
        userIds.forEach { userId ->
            val future = CompletableFuture.supplyAsync({
                try {
                    val request = TokenIssueRequest(userId)
                    val result = mockMvc.perform(
                        post("/api/v1/tokens")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                    ).andReturn()
                    
                    if (result.response.status == 201) {
                        successCount.incrementAndGet()
                        val responseContent = result.response.contentAsString
                        val responseJson = objectMapper.readTree(responseContent)
                        val token = responseJson.get("data").get("token").asText()
                        val queuePosition = responseJson.get("data").get("queuePosition").asInt()
                        "${userId}:${token}:${queuePosition}"
                    } else {
                        "FAILED:${userId}:${result.response.status}"
                    }
                } catch (e: Exception) {
                    "ERROR:${userId}:${e.message}"
                }
            }, executor)
            results.add(future)
        }
        
        val finalResults = results.map { it.get() }
        
        // then - 검증은 실제 비즈니스 로직에 따라 조정
        assert(successCount.get() > 0) // 최소 한 개는 성공해야 함
        
        executor.shutdown()
    }

    @Test
    @DisplayName("토큰 발급 검증 실패 테스트 - 필수 파라미터 누락")
    fun issueToken_Fail_ValidationError() {
        // given - userId가 null인 잘못된 요청
        val invalidRequestJson = """
        {
            "invalidField": "test"
        }
        """.trimIndent()

        // when & then
        mockMvc.perform(
            post("/api/v1/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson)
        )
        .andDo(print())
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.success").value(false))
    }
}