package kr.hhplus.be.server.api.user.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.domain.user.repositories.UserRepository
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@IntegrationTest
class UserIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUser: User

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 데이터 정리
        try {
            val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
            jdbcTemplate.execute("DELETE FROM point_history")
            jdbcTemplate.execute("DELETE FROM balance")
            jdbcTemplate.execute("DELETE FROM users")
        } catch (e: Exception) {
            // 무시
        }

        // 테스트 사용자 생성
        testUser = userRepository.save(User(userId = 1L))
        userRepository.flush()
    }

    describe("사용자 조회 API") {
        context("존재하는 사용자를 조회할 때") {
            it("사용자 정보가 성공적으로 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/users/{userId}", testUser.userId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
            }
        }

        context("존재하지 않는 사용자를 조회할 때") {
            it("404 Not Found를 반환해야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/users/{userId}", 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("잘못된 userId로 조회할 때") {
            it("400 Bad Request를 반환해야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/users/{userId}", -1L)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest)
            }
        }
    }

    describe("사용자 생성 API") {
        context("유효한 요청으로 사용자를 생성할 때") {
            it("사용자가 성공적으로 생성되어야 한다") {
                // given - 중복되지 않을 userId 사용 (매우 큰 숫자로 충돌 방지)
                val uniqueUserId = 99999L + (Math.random() * 100000).toLong()
                val request = UserCreateRequest(userId = uniqueUserId)

                // when & then
                mockMvc.perform(
                    post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").exists())
            }
        }

        context("잘못된 userId로 사용자를 생성하려고 할 때") {
            it("400 Bad Request를 반환해야 한다") {
                // given
                val request = UserCreateRequest(userId = -1L)

                // when & then
                mockMvc.perform(
                    post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("중복된 userId로 사용자를 생성하려고 할 때") {
            it("409 Conflict를 반환해야 한다") {
                // given
                val request = UserCreateRequest(userId = testUser.userId)

                // when & then
                mockMvc.perform(
                    post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }
})