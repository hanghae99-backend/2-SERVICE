package kr.hhplus.be.server.api.balance.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.balance.dto.request.ChargeBalanceRequest
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.repository.UserRepository
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
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class BalanceIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val pointRepository: PointRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUser: User
    lateinit var chargeType: PointHistoryType
    lateinit var deductType: PointHistoryType

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 데이터 정리
        pointRepository.deleteAll()
        userRepository.deleteAll()
        pointHistoryTypeRepository.deleteAll()

        // 테스트 데이터 설정
        testUser = userRepository.save(User.create(1L))

        chargeType = pointHistoryTypeRepository.save(
            PointHistoryType(
                code = "CHARGE",
                name = "충전",
                description = "포인트 충전"
            )
        )

        deductType = pointHistoryTypeRepository.save(
            PointHistoryType(
                code = "DEDUCT",
                name = "사용",
                description = "포인트 사용"
            )
        )

        // 초기 포인트 생성
        pointRepository.save(Point.create(testUser.userId, BigDecimal("50000")))
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
            it("404 Not Found 응답을 반환해야 한다") {
                // given
                val request = ChargeBalanceRequest(
                    userId = 999L,
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
                    amount = BigDecimal("50000000") // 5천만원 충전 시도
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
            it("404 Not Found 응답을 반환해야 한다") {
                // given
                val nonExistentUserId = 999L

                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", nonExistentUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("유효하지 않은 사용자 ID로 조회할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given
                val invalidUserId = -1L

                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", invalidUserId)
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
            it("빈 배열이 반환되어야 한다") {
                // given
                val nonExistentUserId = 999L

                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/history/{userId}", nonExistentUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
            }
        }

        context("유효하지 않은 사용자 ID로 이력 조회할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given
                val invalidUserId = -1L

                // when & then
                mockMvc.perform(
                    get("/api/v1/balance/history/{userId}", invalidUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("동시성 테스트") {
        context("동시에 여러 번 충전 요청을 할 때") {
            it("모든 충전이 정상적으로 처리되어야 한다") {
                // given
                val requests = listOf(
                    ChargeBalanceRequest(testUser.userId, BigDecimal("10000")),
                    ChargeBalanceRequest(testUser.userId, BigDecimal("20000")),
                    ChargeBalanceRequest(testUser.userId, BigDecimal("30000"))
                )

                val executor = Executors.newFixedThreadPool(requests.size)
                val successCount = AtomicInteger(0)

                // when - 동시 충전 요청
                val futures = requests.map { request ->
                    CompletableFuture.supplyAsync({
                        try {
                            val result = mockMvc.perform(
                                post("/api/v1/balance")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 200) {
                                successCount.incrementAndGet()
                            }
                            result.response.status
                        } catch (e: Exception) {
                            500
                        }
                    }, executor)
                }

                futures.forEach { it.get() }

                // then - 모든 요청이 성공해야 함
                assert(successCount.get() == requests.size) {
                    "동시 충전 시 모든 요청이 성공해야 하지만 ${successCount.get()}개만 성공했습니다"
                }

                // 최종 잔액 확인
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", testUser.userId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.balance").value(110000)) // 50000 + 10000 + 20000 + 30000

                executor.shutdown()
            }
        }
    }

    describe("검증 테스트") {
        context("필수 파라미터가 누락된 요청을 할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given - amount가 누락된 잘못된 요청
                val invalidRequestJson = """
                {
                    "userId": ${testUser.userId}
                }
                """.trimIndent()

                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson)
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("잘못된 JSON 형식으로 요청할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given
                val invalidJson = "{ invalid json }"

                // when & then
                mockMvc.perform(
                    post("/api/v1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                )
                .andExpect(status().isBadRequest)
            }
        }
    }
})