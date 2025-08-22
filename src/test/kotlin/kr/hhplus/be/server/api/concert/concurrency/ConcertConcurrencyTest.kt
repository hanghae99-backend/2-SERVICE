package kr.hhplus.be.server.api.concert.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.config.ConcurrencyTest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.config.TestDataCleanupHelper
import kr.hhplus.be.server.config.TestDataFixture
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ConcurrencyTest
class ConcertConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val objectMapper: ObjectMapper,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testUsers: List<User>
    lateinit var testTokens: List<WaitingToken>

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 테스트 데이터 정리
        val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
        TestDataCleanupHelper.cleanupAll(redisTemplate, jdbcTemplate)
        
        // 분산락 통계 초기화
        distributedLock.resetStatistics()

        // TestDataFixture를 사용한 동시성 테스트 환경 구성
        val testEnvironment = TestDataFixture.createConcurrencyTestEnvironment(
            context = webApplicationContext,
            userCount = 10,
            concertTitle = "동시성 테스트 콘서트",
            seatCount = 50
        )
        
        testConcert = testEnvironment.concert
        testSchedule = testEnvironment.schedule
        testUsers = testEnvironment.users
        testTokens = testEnvironment.tokens

        Thread.sleep(100)
    }

    afterEach {
        val stats = distributedLock.getLockStatistics()
        println("""
            === Concert 동시성 테스트 분산락 통계 ===
            성공: ${stats.acquisitionCount}
            실패: ${stats.failureCount}
            평균 대기시간: ${stats.averageWaitTimeMs}ms
            성공률: ${stats.successRate}%
        """.trimIndent())
    }

    describe("콘서트 좌석 예약 동시성 테스트 - 분산락 적용") {
        context("여러 사용자가 동시에 같은 좌석을 예약하려고 할 때") {
            it("분산락으로 하나의 예약만 성공해야 한다") {
                // given
                // 모든 상태 타입 생성 (AVAILABLE, RESERVED, TEMPORARY, CONFIRMED 등)
                TestDataFixture.createAllStatusTypes(webApplicationContext)
                
                // TestDataFixture를 사용해서 테스트용 좌석 생성 (단일 좌석)
                val testSeats = TestDataFixture.createTestSeats(
                    context = webApplicationContext,
                    scheduleId = testSchedule.scheduleId,
                    seatCount = 1,
                    startNumber = 1,
                    price = BigDecimal("80000")
                )
                val testSeat = testSeats.first()

                val executor = Executors.newFixedThreadPool(testUsers.size)
                val latch = CountDownLatch(testUsers.size)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when
                val futures = testUsers.mapIndexed { index, user ->
                    CompletableFuture.supplyAsync({
                        try {
                            latch.countDown()
                            latch.await()
                            
                            val request = ReservationCreateRequest(
                                userId = user.userId,
                                concertId = testConcert.concertId,
                                seatId = testSeat.seatId,
                                token = testTokens[index].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                "SUCCESS"
                            } else {
                                failureCount.incrementAndGet()
                                "FAILURE:${result.response.status}"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR:${e.message}"
                        }
                    }, executor)
                }

                val results = futures.map { it.get(30, TimeUnit.SECONDS) }

                // then
                println("=== 좌석 예약 동시성 결과 ===")
                println("성공: ${successCount.get()}, 실패: ${failureCount.get()}")
                results.forEach { println(it) }
                
                // 하나만 성공해야 함
                successCount.get() shouldBe 1
                failureCount.get() shouldBe (testUsers.size - 1)

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})