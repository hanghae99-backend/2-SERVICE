package kr.hhplus.be.server.api.balance.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BalanceConcurrencyTest(
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

        // 초기 포인트 생성
        pointRepository.save(Point.create(testUser.userId, BigDecimal("10000")))
    }

    describe("잔액 충전 동시성 테스트") {
        context("여러 사용자가 동시에 충전을 요청할 때") {
            it("모든 충전이 안전하게 처리되어야 한다") {
                // given
                val userCount = 5
                val users = (2L..2L + userCount).map { userId ->
                    val user = userRepository.save(User.create(userId))
                    pointRepository.save(Point.create(userId, BigDecimal("10000")))
                    user
                }

                val executor = Executors.newFixedThreadPool(userCount)
                val results = mutableListOf<CompletableFuture<BalanceTestResult>>()
                val successCount = AtomicInteger(0)

                // when - 동시 충전 요청
                users.forEach { user ->
                    val future = CompletableFuture.supplyAsync<BalanceTestResult>({
                        try {
                            val request = ChargeBalanceRequest(
                                userId = user.userId,
                                amount = BigDecimal("50000")
                            )
                            
                            val result = mockMvc.perform(
                                post("/api/v1/balance")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 200) {
                                successCount.incrementAndGet()
                                val responseContent = result.response.contentAsString
                                val responseJson = objectMapper.readTree(responseContent)
                                val currentBalance = responseJson.get("data").get("currentBalance").asLong()
                                BalanceTestResult.Success(user.userId, currentBalance)
                            } else {
                                BalanceTestResult.Failure(user.userId, result.response.status, result.response.contentAsString)
                            }
                        } catch (e: Exception) {
                            BalanceTestResult.Error(user.userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(10, TimeUnit.SECONDS) }

                // then - 검증
                successCount.get() shouldBe userCount
                
                val successResults = finalResults.filterIsInstance<BalanceTestResult.Success>()
                successResults.forEach { result ->
                    result.finalBalance shouldBe 60000L // 10000 + 50000
                }

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("동일한 사용자가 동시에 여러 번 충전을 요청할 때") {
            it("모든 충전이 순차적으로 처리되어야 한다") {
                // given
                val requestCount = 5
                val chargeAmount = BigDecimal("10000")
                val executor = Executors.newFixedThreadPool(requestCount)
                val results = mutableListOf<CompletableFuture<BalanceTestResult>>()
                val successCount = AtomicInteger(0)

                // when - 동일한 사용자로 동시 충전 요청
                repeat(requestCount) {
                    val future = CompletableFuture.supplyAsync<BalanceTestResult>({
                        try {
                            val request = ChargeBalanceRequest(
                                userId = testUser.userId,
                                amount = chargeAmount
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/balance")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 200) {
                                successCount.incrementAndGet()
                                val responseContent = result.response.contentAsString
                                val responseJson = objectMapper.readTree(responseContent)
                                val currentBalance = responseJson.get("data").get("currentBalance").asLong()
                                BalanceTestResult.Success(testUser.userId, currentBalance)
                            } else {
                                BalanceTestResult.Failure(testUser.userId, result.response.status, result.response.contentAsString)
                            }
                        } catch (e: Exception) {
                            BalanceTestResult.Error(testUser.userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(15, TimeUnit.SECONDS) }

                // then - 검증
                successCount.get() shouldBe requestCount

                // 최종 잔액 확인 - 초기 10000 + (10000 * 5) = 60000
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", testUser.userId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.balance").value(60000))

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("충전과 조회가 동시에 수행될 때") {
            it("데이터 일관성이 보장되어야 한다") {
                // given
                val chargeCount = 3
                val readCount = 3
                val totalThreads = chargeCount + readCount
                val executor = Executors.newFixedThreadPool(totalThreads)
                val results = mutableListOf<CompletableFuture<String>>()

                // when - 충전과 조회를 동시에 수행
                // 충전 요청들
                repeat(chargeCount) {
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val request = ChargeBalanceRequest(
                                userId = testUser.userId,
                                amount = BigDecimal("5000")
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/balance")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            "CHARGE:${result.response.status}"
                        } catch (e: Exception) {
                            "CHARGE:ERROR:${e.message}"
                        }
                    }, executor)
                    results.add(future)
                }

                // 조회 요청들
                repeat(readCount) {
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val result = mockMvc.perform(
                                get("/api/v1/balance/{userId}", testUser.userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                            ).andReturn()

                            if (result.response.status == 200) {
                                val responseContent = result.response.contentAsString
                                val responseJson = objectMapper.readTree(responseContent)
                                val balance = responseJson.get("data").get("balance").asLong()
                                "READ:${result.response.status}:${balance}"
                            } else {
                                "READ:${result.response.status}"
                            }
                        } catch (e: Exception) {
                            "READ:ERROR:${e.message}"
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(15, TimeUnit.SECONDS) }

                // then - 모든 요청이 성공적으로 처리되어야 함
                val chargeResults = finalResults.filter { it.startsWith("CHARGE:") }
                val readResults = finalResults.filter { it.startsWith("READ:") }

                chargeResults.count { it == "CHARGE:200" } shouldBe chargeCount
                readResults.count { it.startsWith("READ:200:") } shouldBe readCount

                // 최종 잔액은 10000 + (5000 * 3) = 25000이어야 함
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", testUser.userId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.balance").value(25000))

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }

    describe("데이터베이스 락 테스트") {
        context("DB 락이 필요한 시나리오에서") {
            it("잔액 업데이트의 원자성이 보장되어야 한다") {
                // given
                val threadCount = 10
                val chargeAmount = BigDecimal("1000")
                val executor = Executors.newFixedThreadPool(threadCount)
                val results = mutableListOf<CompletableFuture<BalanceTestResult>>()

                // when - 동시에 작은 금액을 여러 번 충전
                repeat(threadCount) {
                    val future = CompletableFuture.supplyAsync<BalanceTestResult>({
                        try {
                            val request = ChargeBalanceRequest(
                                userId = testUser.userId,
                                amount = chargeAmount
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/balance")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 200) {
                                val responseContent = result.response.contentAsString
                                val responseJson = objectMapper.readTree(responseContent)
                                val currentBalance = responseJson.get("data").get("currentBalance").asLong()
                                BalanceTestResult.Success(testUser.userId, currentBalance)
                            } else {
                                BalanceTestResult.Failure(testUser.userId, result.response.status, result.response.contentAsString)
                            }
                        } catch (e: Exception) {
                            BalanceTestResult.Error(testUser.userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(20, TimeUnit.SECONDS) }

                // then - 모든 충전이 성공하고 최종 잔액이 정확해야 함
                val successResults = finalResults.filterIsInstance<BalanceTestResult.Success>()
                successResults.size shouldBe threadCount

                // 최종 잔액 확인 - 초기 10000 + (1000 * 10) = 20000
                mockMvc.perform(
                    get("/api/v1/balance/{userId}", testUser.userId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.balance").value(20000))

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})

sealed class BalanceTestResult {
    data class Success(val userId: Long, val finalBalance: Long) : BalanceTestResult()
    data class Failure(val userId: Long, val statusCode: Int, val response: String) : BalanceTestResult()
    data class Error(val userId: Long, val message: String) : BalanceTestResult()
}
