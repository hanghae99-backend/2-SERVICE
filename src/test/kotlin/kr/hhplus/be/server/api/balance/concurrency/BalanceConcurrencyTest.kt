package kr.hhplus.be.server.api.balance.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.IsolationMode
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
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import jakarta.persistence.EntityManager
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
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

    // 테스트 격리를 위한 설정
    isolationMode = IsolationMode.InstancePerTest  // 각 테스트마다 새 인스턴스

    lateinit var mockMvc: MockMvc
    lateinit var testUser: User
    lateinit var chargeType: PointHistoryType

    // 각 테스트마다 고유한 userId 사용
    val testUserId = System.currentTimeMillis() % 100000

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
        val entityManager = webApplicationContext.getBean(EntityManager::class.java)
        val pointRepository = webApplicationContext.getBean(PointRepository::class.java)
        val transactionManager = webApplicationContext.getBean(PlatformTransactionManager::class.java)
        val transactionTemplate = TransactionTemplate(transactionManager)

        // 분산락 통계 초기화 (트랜잭션 밖에서)
        distributedLock.resetStatistics()

        // 전체 데이터 초기화 및 생성을 하나의 트랜잭션으로 처리
        transactionTemplate.execute { _ ->
            // 1. 테스트 데이터 정리
            println("[BeforeEach] 테스트 데이터 정리 시작")
            TestDataCleanupHelper.cleanupAll(redisTemplate, jdbcTemplate)

            // 2. 영속성 컨텍스트 클리어 (정리 후)
            entityManager.flush()
            entityManager.clear()

            // 3. DB 상태 확인 (정리 후)
            val countAfterCleanup = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point", Int::class.java)
            println("[BeforeEach] 정리 후 Point 테이블 레코드 수: $countAfterCleanup")

            // 4. 새로운 테스트 데이터 생성 (같은 트랜잭션 내에서)
            println("[BeforeEach] 테스트 데이터 생성 시작 - userId: $testUserId")
            testUser = TestDataFixture.createTestUser(
                context = webApplicationContext,
                userId = testUserId,
                withPoints = true,
                pointAmount = BigDecimal("10000")
            )

            // 5. flush하여 DB에 즉시 반영
            entityManager.flush()

            // 6. 생성 확인
            val savedPoint = pointRepository.findByUserId(testUser.userId)
            val countInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM point WHERE user_id = ?",
                Int::class.java,
                testUser.userId
            )
            println("[BeforeEach] 트랜잭션 내 DB 저장 확인 - userId: ${testUser.userId}, JPA 조회: ${savedPoint != null}, JDBC COUNT: $countInDb, balance: ${savedPoint?.amount}")
        }

        // 트랜잭션 커밋 후 영속성 컨텍스트 clear
        entityManager.clear()

        // 트랜잭션 외부에서 최종 확인
        val finalCheck = transactionTemplate.execute { _ ->
            val point = pointRepository.findByUserId(testUser.userId)
            println("[BeforeEach] 최종 확인 - userId: ${testUser.userId}, point exists: ${point != null}, balance: ${point?.amount}")
            point
        }

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
                val baseUserId = System.currentTimeMillis() % 100000  // 고유한 base userId
                val users = TestDataFixture.createSimpleUsers(
                    context = webApplicationContext,
                    userCount = userCount,
                    startUserId = baseUserId
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
            it("분산락으로 인해 하나의 요청만 성공하고 나머지는 429 에러를 반환해야 한다") {
                // given
                val requestCount = 5
                val chargeAmount = BigDecimal("10000")
                val executor = Executors.newFixedThreadPool(requestCount)
                val latch = CountDownLatch(requestCount)
                val successCount = AtomicInteger(0)
                val tooManyRequestsCount = AtomicInteger(0)

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

                            when (result.response.status) {
                                200 -> {
                                    successCount.incrementAndGet()
                                    "SUCCESS"
                                }
                                429 -> {
                                    tooManyRequestsCount.incrementAndGet()
                                    "TOO_MANY_REQUESTS"
                                }
                                else -> "FAILURE: ${result.response.status}"
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
                println("429 에러: ${tooManyRequestsCount.get()}")
                results.forEach { println(it) }

                // 분산락의 정상 동작: 하나만 성공, 나머지는 429 에러
                successCount.get() shouldBe 1
                tooManyRequestsCount.get() shouldBe (requestCount - 1)

                // 최종 잔액 검증: 초기 잔액 + 한 번의 충전만 반영되어야 함
                val expectedBalance = 10000 + 10000  // 10000 + (10000 * 1)
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