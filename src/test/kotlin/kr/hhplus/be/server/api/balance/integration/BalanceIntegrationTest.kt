package kr.hhplus.be.server.api.balance.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.balance.dto.request.ChargeBalanceRequest
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.domain.user.repositories.UserRepository
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

@IntegrationTest
class BalanceIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUser: User

    beforeSpec {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }
    
    beforeEach {
        // 외래키 제약 조건 순서에 따른 데이터 삭제
        // 1. point_history가 point_history_type을 참조하므로 먼저 삭제
        pointHistoryRepository.deleteAll()
        // 2. point가 user를 참조하므로 point 먼저 삭제
        pointRepository.deleteAll()
        // 3. 나머지 삭제
        pointHistoryTypeRepository.deleteAll()
        userRepository.deleteAll()
        
        // 새로운 테스트 사용자 생성
        testUser = userRepository.save(User.create())

        // PointHistoryType 설정
        pointHistoryTypeRepository.save(
            PointHistoryType(
                code = "CHARGE",
                name = "충전",
                description = "포인트 충전"
            )
        )

        pointHistoryTypeRepository.save(
            PointHistoryType(
                code = "DEDUCT",
                name = "사용",
                description = "포인트 사용"
            )
        )

        // 초기 포인트 생성
        pointRepository.save(Point.create(testUser.userId, BigDecimal("50000")))
    }
    
    afterEach {
        // 외래키 제약 조건 순서에 따른 데이터 삭제
        pointHistoryRepository.deleteAll()
        pointRepository.deleteAll()
        pointHistoryTypeRepository.deleteAll()
        userRepository.deleteAll()
    }

    describe("잔액 충전 API") {
        context("유효한 충전 요청을 할 때") {
            it("잔액이 정상적으로 충전되어야 한다") {
                // given
                val request = ChargeBalanceRequest(
                    userId = testUser.userId,
                    amount = BigDecimal("100000")
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("잔액 충전이 완료되었습니다"))
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.chargedAmount").value(100000))
                .andExpect(jsonPath("$.data.currentBalance").value(150000))
            }
        }

        context("음수 금액으로 충전 요청을 할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given
                val request = ChargeBalanceRequest(
                    userId = testUser.userId,
                    amount = BigDecimal("-10000")
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("최소 충전 금액 미만으로 요청할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given
                val request = ChargeBalanceRequest(
                    userId = testUser.userId,
                    amount = BigDecimal("500")
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("존재하지 않는 사용자로 충전 요청할 때") {
            it("오류 응답을 반환해야 한다") {
                // given
                val request = ChargeBalanceRequest(
                    userId = 999999L,
                    amount = BigDecimal("100000")
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("최대 잔액 한도를 초과하는 금액으로 충전 요청할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given
                val request = ChargeBalanceRequest(
                    userId = testUser.userId,
                    amount = BigDecimal("50000000")
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("잔액 조회 API") {
        context("존재하는 사용자의 잔액을 조회할 때") {
            it("잔액 정보가 정상적으로 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", testUser.userId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("잔액 조회가 완료되었습니다"))
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.balance").value(50000))
            }
        }

        context("존재하지 않는 사용자의 잔액을 조회할 때") {
            it("오류 응답을 반환해야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("유효하지 않은 사용자 ID로 조회할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", -1L)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("포인트 이력 조회 API") {
        context("사용자의 포인트 이력을 조회할 때") {
            it("이력 정보가 배열로 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/history/{userId}", testUser.userId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("포인트 이력 조회가 완료되었습니다"))
                .andExpect(jsonPath("$.data").isArray())
            }
        }

        context("존재하지 않는 사용자의 이력을 조회할 때") {
            it("오류 응답 또는 빈 배열이 반환되어야 한다") {
                // when & then
                val result = mockMvc.perform(
                    get("/api/v1/balance/history/{userId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                
                val status = result.andReturn().response.status
                if (status == 404) {
                    result.andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                } else {
                    result.andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").isArray())
                        .andExpect(jsonPath("$.data").isEmpty())
                }
            }
        }

        context("유효하지 않은 사용자 ID로 이력 조회할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/history/{userId}", -1L)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }
})