package kr.hhplus.be.server.api.user.integration

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
class UserIntegrationTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

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

    @Nested
    @DisplayName("사용자 생성 API 테스트")
    inner class CreateUserTest {

        @Test
        @DisplayName("유효한 사용자 생성 요청 시 사용자가 정상적으로 생성되어야 한다")
        fun createUser_Success() {
            // given
            val userId = 12345L
            val createRequest = UserCreateRequest(userId = userId)

            // when & then
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest))
            )
                .andDo(print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("사용자 생성 성공"))
                .andExpect(jsonPath("$.data.userId").value(userId))

            // DB에 실제로 저장되었는지 확인
            val savedUser = userJpaRepository.findById(userId)
            assert(savedUser.isPresent) { "사용자가 DB에 저장되지 않았습니다" }
            assert(savedUser.get().userId == userId) { "저장된 사용자 ID가 일치하지 않습니다" }
        }

        @Test
        @DisplayName("중복된 사용자 ID로 생성 요청 시 400 오류가 발생해야 한다")
        fun createUser_Fail_DuplicateUserId() {
            // given
            val userId = 99999L
            val createRequest = UserCreateRequest(userId = userId)
            
            // 첫 번째 생성
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest))
            )
                .andExpect(status().isCreated)

            // when & then - 두 번째 생성 (중복)
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest))
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("잘못된 userId로 생성 요청 시 400 오류가 발생해야 한다")
        fun createUser_Fail_InvalidUserId() {
            // given
            val invalidRequest = UserCreateRequest(userId = -1L) // 음수

            // when & then
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("0으로 생성 요청 시 400 오류가 발생해야 한다")
        fun createUser_Fail_ZeroUserId() {
            // given
            val invalidRequest = UserCreateRequest(userId = 0L)

            // when & then
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("사용자 조회 API 테스트")
    inner class GetUserTest {

        @Test
        @DisplayName("존재하는 사용자 조회 시 사용자 정보가 정상적으로 반환되어야 한다")
        fun getUser_Success() {
            // given
            val userId = 54321L
            val createRequest = UserCreateRequest(userId = userId)
            
            // 사용자 생성
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest))
            )
                .andExpect(status().isCreated)

            // when & then - 사용자 조회
            mockMvc.perform(
                get("/api/v1/users/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("사용자 조회 성공"))
                .andExpect(jsonPath("$.data.userId").value(userId))
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 404 오류가 발생해야 한다")
        fun getUser_Fail_UserNotFound() {
            // given
            val nonExistentUserId = 88888L

            // when & then
            mockMvc.perform(
                get("/api/v1/users/{userId}", nonExistentUserId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("잘못된 userId로 조회 시 400 오류가 발생해야 한다")
        fun getUser_Fail_InvalidUserId() {
            // given
            val invalidUserId = -1L

            // when & then
            mockMvc.perform(
                get("/api/v1/users/{userId}", invalidUserId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    inner class ConcurrencyTest {

        @Test
        @DisplayName("동시에 여러 사용자 생성 시 모든 사용자가 정상적으로 생성되어야 한다")
        fun createUser_Concurrency_MultipleUsersSuccess() {
            // given
            val userIds = listOf(10001L, 10002L, 10003L, 10004L, 10005L)
            val executor = Executors.newFixedThreadPool(userIds.size)
            val successCount = AtomicInteger(0)

            // when - 동시 사용자 생성 요청
            val futures = userIds.map { userId ->
                CompletableFuture.supplyAsync({
                    try {
                        val createRequest = UserCreateRequest(userId = userId)
                        val result = mockMvc.perform(
                            post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest))
                        ).andReturn()
                        
                        if (result.response.status == 201) {
                            successCount.incrementAndGet()
                        }
                        result.response.status
                    } catch (e: Exception) {
                        500
                    }
                }, executor)
            }

            futures.forEach { it.get() }

            // then - 모든 사용자가 성공적으로 생성되어야 함
            assert(successCount.get() == userIds.size) { 
                "동시 사용자 생성 시 모든 요청이 성공해야 하지만 ${successCount.get()}개만 성공했습니다" 
            }

            // DB에 모든 사용자가 저장되었는지 확인
            userIds.forEach { userId ->
                val savedUser = userJpaRepository.findById(userId)
                assert(savedUser.isPresent) { "사용자 $userId 가 DB에 저장되지 않았습니다" }
                assert(savedUser.get().userId == userId) { "저장된 사용자 ID가 일치하지 않습니다" }
            }

            executor.shutdown()
        }

        @Test
        @DisplayName("동시에 같은 userId로 생성 요청 시 하나의 요청만 성공해야 한다")
        fun createUser_Concurrency_DuplicateUserIdOnlyOneSucceeds() {
            // given
            val userId = 20001L
            val requestCount = 3
            val executor = Executors.newFixedThreadPool(requestCount)
            val successCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)

            // when - 동시에 같은 userId로 생성 요청
            val futures = (1..requestCount).map {
                CompletableFuture.supplyAsync({
                    try {
                        val createRequest = UserCreateRequest(userId = userId)
                        val result = mockMvc.perform(
                            post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest))
                        ).andReturn()
                        
                        when (result.response.status) {
                            201 -> successCount.incrementAndGet()
                            400 -> errorCount.incrementAndGet()
                            else -> { /* 다른 상태 코드는 무시 */ }
                        }
                        result.response.status
                    } catch (e: Exception) {
                        500
                    }
                }, executor)
            }

            futures.forEach { it.get() }

            // then - 하나만 성공, 나머지는 중복 오류
            assert(successCount.get() == 1) { 
                "동시 중복 생성 시 하나만 성공해야 하지만 ${successCount.get()}개가 성공했습니다" 
            }
            assert(errorCount.get() == (requestCount - 1)) { 
                "중복 오류는 ${requestCount - 1}개 발생해야 하지만 ${errorCount.get()}개가 발생했습니다" 
            }

            // DB에 하나만 저장되었는지 확인
            val savedUser = userJpaRepository.findById(userId)
            assert(savedUser.isPresent) { "사용자가 DB에 저장되지 않았습니다" }
            assert(savedUser.get().userId == userId) { "저장된 사용자 ID가 일치하지 않습니다" }

            executor.shutdown()
        }
    }

    @Nested
    @DisplayName("검증 테스트")
    inner class ValidationTest {

        @Test
        @DisplayName("잘못된 JSON 형식으로 요청 시 400 오류가 발생해야 한다")
        fun createUser_Fail_InvalidJson() {
            // given
            val invalidJson = "{ invalid json }"

            // when & then
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson)
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("필수 파라미터가 누락된 요청 시 400 오류가 발생해야 한다")
        fun createUser_Fail_MissingRequiredParameter() {
            // given - userId가 null인 잘못된 요청
            val invalidRequestJson = """
            {
                "invalidField": "test"
            }
            """.trimIndent()

            // when & then
            mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequestJson)
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }
    }
}