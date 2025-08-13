package kr.hhplus.be.server.api.user.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.config.TestDataCleanupService
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

@IntegrationTest
class UserIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val userJpaRepository: UserJpaRepository,
    private val testDataCleanupService: TestDataCleanupService,
    private val objectMapper: ObjectMapper
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc

    beforeSpec {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }
    
    beforeEach {
        // 안전한 데이터 정리
        testDataCleanupService.cleanupAllTestData()
    }
    
    afterEach {
        // 각 테스트 후 데이터 정리
        try {
            testDataCleanupService.cleanupAllTestData()
        } catch (e: Exception) {
            println("Cleanup failed: ${e.message}")
        }
    }

    describe("사용자 생성 API") {
        context("유효한 사용자 생성 요청을 할 때") {
            it("사용자가 정상적으로 생성되어야 한다") {
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
        }

        context("중복된 사용자 ID로 생성 요청을 할 때") {
            it("409 오류가 발생해야 한다") {
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
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("잘못된 userId로 생성 요청을 할 때") {
            it("400 오류가 발생해야 한다") {
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
        }

        context("0으로 생성 요청을 할 때") {
            it("400 오류가 발생해야 한다") {
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
    }

    describe("사용자 조회 API") {
        context("존재하는 사용자를 조회할 때") {
            it("사용자 정보가 정상적으로 반환되어야 한다") {
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
        }

        context("존재하지 않는 사용자를 조회할 때") {
            it("404 오류가 발생해야 한다") {
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
        }

        context("잘못된 userId로 조회할 때") {
            it("400 오류가 발생해야 한다") {
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
    }

    describe("검증 테스트") {
        context("잘못된 JSON 형식으로 요청할 때") {
            it("400 오류가 발생해야 한다") {
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
        }

        context("필수 파라미터가 누락된 요청을 할 때") {
            it("400 오류가 발생해야 한다") {
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

        context("Content-Type이 JSON이 아닐 때") {
            it("415 오류가 발생해야 한다") {
                // given
                val userId = 12345L
                val createRequest = UserCreateRequest(userId = userId)

                // when & then
                mockMvc.perform(
                    post("/api/v1/users")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(objectMapper.writeValueAsString(createRequest))
                )
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType)
            }
        }

        context("빈 요청 본문으로 요청할 때") {
            it("400 오류가 발생해야 한다") {
                // given
                val emptyContent = ""

                // when & then
                mockMvc.perform(
                    post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyContent)
                )
                .andDo(print())
                .andExpect(status().isBadRequest)
            }
        }
    }
})
