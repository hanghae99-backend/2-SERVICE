package kr.hhplus.be.server.api.balance.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import kr.hhplus.be.server.api.balance.dto.request.ChargeBalanceRequest
import kr.hhplus.be.server.config.ConcurrencyTest
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.config.TestDataConstants
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.config.TestDataCleanupHelper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ConcurrencyTest
class BalanceConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUser: User
    lateinit var chargeType: PointHistoryType

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 테스트 데이터 정리
        val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
        TestDataCleanupHelper.cleanupAll(redisTemplate, jdbcTemplate)
        
        // 분산락 통계 초기화
        distributedLock.resetStatistics()

        // 테스트 데이터 설정
        testUser = TestDataFixture.createTestUser(
            context = webApplicationContext, 
            userId = 1L, 
            withPoints = true,
            pointAmount = BigDecimal("10000")
        )

        chargeType = TestDataFixture.createPointHistoryType(
            code = TestDataConstants.PointHistoryType.CHARGE.code,
            name = TestDataConstants.PointHistoryType.CHARGE.name,
            description = TestDataConstants.PointHistoryType.CHARGE.description
        )
    }

    afterEach {
        // 분산락 통계 출력
        val stats = distributedLock.getLockStatistics()
        println("""
            === Balance 동시성 테스트 분산락 통계 ===
            성공: ${stats.acquisitionCount}
            실패: ${stats.failureCount}
            평균 대기시간: ${stats.averageWaitTimeMs}ms
            성공률: ${stats.successRate}%
        """.trimIndent())
    }

    describe("잔액 충전 동시성 테스트 - 분산락 적용") {
        context("여러 사용자가 동시에 충전을 요청할 때") {
            it("분산락으로 모든 충전이 안전하게 처리되어야 한다") {
                // given
                val userCount = 5
                val users = TestDataFixture.createSimpleUsers(
                    context = webApplicationContext,
                    userCount = userCount,
                    startUserId = 1L
                )

                val executor = Executors.newFixedThreadPool(userCount)
                val latch = CountDownLatch(userCount)
                val successCount = AtomicInteger(0)

                // when - 동시 충전 요청
                val futures = users.map { user ->
                    CompletableFuture.supplyAsync({
                        try {
                            latch.countDown()
                            latch.await() // 동시 시작
                            
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
                                "SUCCESS"
                            } else {
                                "FAILURE: ${result.response.status}"
                            }
                        } catch (e: Exception) {
                            "ERROR: ${e.message}"
                        }
                    }, executor)
                }

                val results = futures.map { it.get(30, TimeUnit.SECONDS) }

                // then
                println("=== 여러 사용자 동시 충전 결과 ===")
                println("성공: ${successCount.get()}")
                results.forEach { println(it) }
                
                // 모든 요청이 성공해야 함 (서로 다른 사용자)
                successCount.get() shouldBe userCount

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("동일한 사용자가 동시에 여러 번 충전을 요청할 때") {
            it("분산락으로 순차적으로 처리되어야 한다") {
                // given
                val requestCount = 5
                val chargeAmount = BigDecimal("10000")
                val executor = Executors.newFixedThreadPool(requestCount)
                val latch = CountDownLatch(requestCount)
                val successCount = AtomicInteger(0)

                // when
                val futures = (0 until requestCount).map {
                    CompletableFuture.supplyAsync({
                        try {
                            latch.countDown()
                            latch.await()
                            
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
                                "SUCCESS"
                            } else {
                                "FAILURE: ${result.response.status}"
                            }
                        } catch (e: Exception) {
                            "ERROR: ${e.message}"
                        }
                    }, executor)
                }

                val results = futures.map { it.get(30, TimeUnit.SECONDS) }

                // then
                println("=== 동일 사용자 동시 충전 결과 ===")
                println("성공: ${successCount.get()}")
                results.forEach { println(it) }
                
                // 모든 요청이 성공해야 함 (분산락으로 순차 처리)
                successCount.get() shouldBe requestCount

                // 최종 잔액 검증
                val expectedBalance = 10000 + (10000 * requestCount)
                val result = mockMvc.perform(
                    get("/api/v1/balance/{userId}", testUser.userId)
                ).andReturn()
                
                val responseJson = objectMapper.readTree(result.response.contentAsString)
                val actualBalance = responseJson.get("data").get("balance").asLong()
                actualBalance shouldBe expectedBalance

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