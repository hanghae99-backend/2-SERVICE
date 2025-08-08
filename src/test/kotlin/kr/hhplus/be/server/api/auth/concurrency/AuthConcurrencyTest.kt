package kr.hhplus.be.server.api.auth.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.model.User
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuthConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val userJpaRepository: UserJpaRepository,
    private val tokenStore: TokenStore,
    private val objectMapper: ObjectMapper
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
        
        // 테스트 데이터 초기화
        userJpaRepository.deleteAll()
    }

    describe("토큰 발급 동시성 테스트") {
        context("여러 사용자가 동시에 토큰 발급을 요청할 때") {
            it("모든 요청이 안전하게 처리되어야 한다") {
                // given
                val userCount = 10
                val userIds = (5000L until 5000L + userCount).toList()
                
                // 사용자들 미리 생성
                userIds.forEach { userId ->
                    userJpaRepository.save(User.create(userId))
                }
                
                val executor = Executors.newFixedThreadPool(userCount)
                val results = mutableListOf<CompletableFuture<TestResult>>()
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)
                val queuePositions = mutableSetOf<Int>()

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
                                
                                synchronized(queuePositions) {
                                    queuePositions.add(queuePosition)
                                }
                                
                                TestResult.Success(userId, token, queuePosition)
                            } else {
                                failureCount.incrementAndGet()
                                TestResult.Failure(userId, result.response.status, result.response.contentAsString)
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            TestResult.Error(userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }
                
                val finalResults = results.map { it.get(10, TimeUnit.SECONDS) }
                
                // then - 검증
                successCount.get() shouldBeGreaterThan 0
                
                // 대기열 순서 검증 - 중복되지 않은 연속된 숫자여야 함
                val sortedPositions = queuePositions.sorted()
                sortedPositions.size shouldBe successCount.get()
                
                // 대기열 순서가 1부터 시작하는 연속된 숫자인지 확인
                sortedPositions.forEachIndexed { index, position ->
                    position shouldBe (index + 1)
                }
                
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("동일한 사용자가 동시에 여러 번 토큰 발급을 요청할 때") {
            it("중복 토큰 발급을 방지해야 한다") {
                // given
                val userId = 6000L
                val user = User.create(userId)
                userJpaRepository.save(user)
                
                val requestCount = 5
                val executor = Executors.newFixedThreadPool(requestCount)
                val results = mutableListOf<CompletableFuture<TestResult>>()
                val successCount = AtomicInteger(0)
                val duplicateCount = AtomicInteger(0)

                // when - 동일한 사용자로 동시 요청
                repeat(requestCount) {
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val request = TokenIssueRequest(userId)
                            val result = mockMvc.perform(
                                post("/api/v1/tokens")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()
                            
                            when (result.response.status) {
                                201 -> {
                                    successCount.incrementAndGet()
                                    val responseContent = result.response.contentAsString
                                    val responseJson = objectMapper.readTree(responseContent)
                                    val token = responseJson.get("data").get("token").asText()
                                    val queuePosition = responseJson.get("data").get("queuePosition").asInt()
                                    TestResult.Success(userId, token, queuePosition)
                                }
                                409 -> { // Conflict - 이미 토큰이 존재하는 경우
                                    duplicateCount.incrementAndGet()
                                    TestResult.Duplicate(userId, result.response.contentAsString)
                                }
                                else -> {
                                    TestResult.Failure(userId, result.response.status, result.response.contentAsString)
                                }
                            }
                        } catch (e: Exception) {
                            TestResult.Error(userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }
                
                val finalResults = results.map { it.get(10, TimeUnit.SECONDS) }
                
                // then - 검증
                // 성공은 최대 1개, 나머지는 중복 또는 실패여야 함
                successCount.get() shouldBe 1
                (duplicateCount.get() + successCount.get()) shouldBe requestCount
                
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("대기열 순서 조회를 동시에 수행할 때") {
            it("일관된 결과를 반환해야 한다") {
                // given
                val userId = 7000L
                val user = User.create(userId)
                userJpaRepository.save(user)
                
                // 먼저 토큰 발급
                val request = TokenIssueRequest(userId)
                val issueResult = mockMvc.perform(
                    post("/api/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                ).andReturn()
                
                val responseContent = issueResult.response.contentAsString
                val responseJson = objectMapper.readTree(responseContent)
                val token = responseJson.get("data").get("token").asText()
                
                val readCount = 5
                val executor = Executors.newFixedThreadPool(readCount)
                val results = mutableListOf<CompletableFuture<String>>()

                // when - 동시에 토큰 상태 조회
                repeat(readCount) {
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val result = mockMvc.perform(
                                get("/api/v1/tokens/{token}", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                            ).andReturn()
                            
                            if (result.response.status == 200) {
                                val content = result.response.contentAsString
                                val json = objectMapper.readTree(content)
                                json.get("data").get("status").asText()
                            } else {
                                "ERROR:${result.response.status}"
                            }
                        } catch (e: Exception) {
                            "EXCEPTION:${e.message}"
                        }
                    }, executor)
                    results.add(future)
                }
                
                val finalResults = results.map { it.get(10, TimeUnit.SECONDS) }
                
                // then - 모든 응답이 동일해야 함
                val distinctResults = finalResults.distinct()
                distinctResults.size shouldBe 1
                distinctResults.first() shouldBe "WAITING"
                
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }

    describe("데이터베이스 락 테스트") {
        context("DB 락이 필요한 시나리오에서") {
            it("데이터 일관성이 보장되어야 한다") {
                // given
                val userCount = 3
                val userIds = (8000L until 8000L + userCount).toList()
                
                // 사용자들 미리 생성
                userIds.forEach { userId ->
                    userJpaRepository.save(User.create(userId))
                }
                
                val executor = Executors.newFixedThreadPool(userCount)
                val results = mutableListOf<CompletableFuture<TestResult>>()

                // DB 트랜잭션과 함께 실행하는 헬퍼 함수
                fun executeDbTransaction(userId: Long): TestResult {
                    return try {
                        val request = TokenIssueRequest(userId)
                        val result = mockMvc.perform(
                            post("/api/v1/tokens")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                        ).andReturn()
                        
                        if (result.response.status == 201) {
                            val responseContent = result.response.contentAsString
                            val responseJson = objectMapper.readTree(responseContent)
                            val token = responseJson.get("data").get("token").asText()
                            val queuePosition = responseJson.get("data").get("queuePosition").asInt()
                            TestResult.Success(userId, token, queuePosition)
                        } else {
                            TestResult.Failure(userId, result.response.status, result.response.contentAsString)
                        }
                    } catch (e: Exception) {
                        TestResult.Error(userId, e.message ?: "Unknown error")
                    }
                }

                // when - 동시에 토큰 발급 요청 (DB 락 테스트)
                userIds.forEach { userId ->
                    val future = CompletableFuture.supplyAsync<TestResult>({
                        executeDbTransaction(userId)
                    }, executor)
                    results.add(future)
                }
                
                val finalResults = results.map { it.get(15, TimeUnit.SECONDS) }
                
                // then - 모든 요청이 성공적으로 처리되어야 함
                val successResults = finalResults.filterIsInstance<TestResult.Success>()
                successResults.size shouldBe userCount
                
                // 대기열 순서가 올바르게 부여되었는지 확인
                val queuePositions = successResults.map { it.queuePosition }.sorted()
                queuePositions shouldBe (1..userCount).toList()
                
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})

sealed class TestResult {
    data class Success(val userId: Long, val token: String, val queuePosition: Int) : TestResult()
    data class Failure(val userId: Long, val statusCode: Int, val response: String) : TestResult()
    data class Error(val userId: Long, val message: String) : TestResult()
    data class Duplicate(val userId: Long, val response: String) : TestResult()
}
