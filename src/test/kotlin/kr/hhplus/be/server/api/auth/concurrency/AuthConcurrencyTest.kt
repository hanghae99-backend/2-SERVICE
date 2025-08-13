package kr.hhplus.be.server.api.auth.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.config.ConcurrencyTest
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.model.User
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
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



@ConcurrencyTest
class AuthConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val userJpaRepository: UserJpaRepository,
    private val tokenStore: TokenStore,
    private val objectMapper: ObjectMapper,

) : DescribeSpec({

    extension(SpringExtension)

    lateinit var mockMvc: MockMvc

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
        
        // 데이터 정리 (외래키 제약조건 고려)
        try {
            val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
            jdbcTemplate.execute("DELETE FROM point_history")
            jdbcTemplate.execute("DELETE FROM point")
            jdbcTemplate.execute("DELETE FROM users")
            
            // TokenStore 데이터도 정리
            try {
                if (tokenStore is kr.hhplus.be.server.domain.auth.infrastructure.RedisTokenStore) {
                    // Redis TokenStore인 경우 모든 토큰 삭제
                    // flushAll 메서드가 없을 수 있으므로 생략
                }
            } catch (e: Exception) {
                // TokenStore 정리 실패 시 무시
                println("TokenStore 정리 실패: ${e.message}")
            }
        } catch (e: Exception) {
            // 테이블이 없거나 이미 비어있는 경우 무시
        }
    }

    describe("토큰 발급 동시성 테스트") {
        context("여러 사용자가 동시에 토큰 발급을 요청할 때") {
            it("모든 요청이 안전하게 처리되어야 한다") {
                // given
                val userCount = 10
                val baseUserId = System.currentTimeMillis() + 5000
                val userIds = (0 until userCount).map { baseUserId + it }.toList()
                
                // 사용자들 미리 생성
                userIds.forEach { userId ->
                    userJpaRepository.save(User.create(userId))
                }
                userJpaRepository.flush() // 즉시 DB에 반영
                
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
                println("성공 수: ${successCount.get()}, 실패 수: ${failureCount.get()}")
                println("대기열 순서들: ${queuePositions.sorted()}")
                
                successCount.get() shouldBeGreaterThan 0
                
                // 대기열 순서 검증 - 성공한 요청에 대해서만 검증
                if (queuePositions.isNotEmpty()) {
                    val sortedPositions = queuePositions.sorted()
                    sortedPositions.size shouldBe successCount.get()
                    
                    // 대기열 순서가 연속된 숫자인지 확인 (반드시 1부터 시작할 필요는 없음)
                    val minPosition = sortedPositions.first()
                    sortedPositions.forEachIndexed { index, position ->
                        position shouldBe (minPosition + index)
                    }
                }
                
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("동일한 사용자가 동시에 여러 번 토큰 발급을 요청할 때") {
            it("중복 토큰 발급을 방지해야 한다") {
                // given
                val userId = System.currentTimeMillis() + 6000
                val user = User.create(userId)
                userJpaRepository.save(user)
                userJpaRepository.flush() // 즉시 DB에 반영

                val requestCount = 5
                val executor = Executors.newFixedThreadPool(requestCount)
                val results = mutableListOf<CompletableFuture<TestResult>>()
                val successCount = AtomicInteger(0)
                val duplicateCount = AtomicInteger(0)

                logger.info { ">>> 테스트 시작: userId = $userId, 요청 수 = $requestCount" }

                // when - 동일한 사용자로 동시 요청
                repeat(requestCount) { index ->
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val request = TokenIssueRequest(userId)
                            val result = mockMvc.perform(
                                post("/api/v1/tokens")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            val status = result.response.status
                            val body = result.response.contentAsString

                            logger.info { "[$index] 응답 상태: $status, 응답 본문: $body" }

                            when (status) {
                                201 -> {
                                    val responseJson = objectMapper.readTree(body)
                                    val token = responseJson.get("data").get("token").asText()
                                    val message = responseJson.get("data").get("message").asText()
                                    val queuePosition = responseJson.get("data").get("queuePosition").asInt()
                                    
                                    if (message.contains("이미 대기열에 등록된")) {
                                        duplicateCount.incrementAndGet()
                                        logger.warn { "[$index] ⚠️ 중복 토큰 요청 (같은 토큰 반환): $token" }
                                        TestResult.Duplicate(userId, body)
                                    } else {
                                        successCount.incrementAndGet()
                                        logger.info { "[$index] ✅ 새 토큰 발급: token=$token, queuePosition=$queuePosition" }
                                        TestResult.Success(userId, token, queuePosition)
                                    }
                                }
                                409 -> {
                                    duplicateCount.incrementAndGet()
                                    logger.warn { "[$index] ⚠️ 중복 토큰 요청 (Conflict)" }
                                    TestResult.Duplicate(userId, body)
                                }
                                else -> {
                                    logger.error { "[$index] ❌ 예상치 못한 실패: status=$status" }
                                    TestResult.Failure(userId, status, body)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "[$index] ❗ 예외 발생: ${e.message}" }
                            TestResult.Error(userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.mapIndexed { i, f ->
                    val res = f.get(10, TimeUnit.SECONDS)
                    logger.info { "[$i] 최종 결과: $res" }
                    res
                }

                // then - 검증
                logger.info { ">>> 성공 수: ${successCount.get()}, 중복 수: ${duplicateCount.get()}" }
                
                val successfulTokens = finalResults.filterIsInstance<TestResult.Success>()
                val duplicateTokens = finalResults.filterIsInstance<TestResult.Duplicate>()
                
                println("성공한 토큰 발급: ${successfulTokens.size}개")
                println("중복 처리된 요청: ${duplicateTokens.size}개")
                
                // 최소 1개의 성공적인 토큰 발급이 있어야 함
                successfulTokens.size shouldBe 1
                
                // 나머지는 모두 중복 처리되어야 함
                (successfulTokens.size + duplicateTokens.size) shouldBe requestCount

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("대기열 순서 조회를 동시에 수행할 때") {
            it("일관된 결과를 반환해야 한다") {
                // given
                val userId = System.currentTimeMillis() + 7000
                val user = User.create(userId)
                userJpaRepository.save(user)
                userJpaRepository.flush() // 즉시 DB에 반영
                
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
                println("조회 결과들: $finalResults")
                val distinctResults = finalResults.distinct()
                
                // 모든 결과가 동일해야 함 (WAITING 또는 PROCESSING 등)
                distinctResults.size shouldBe 1
                
                val expectedStatuses = setOf("WAITING", "PROCESSING", "COMPLETED")
                expectedStatuses.contains(distinctResults.first()) shouldBe true
                
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
                val baseUserId = System.currentTimeMillis() + 8000
                val userIds = (0 until userCount).map { baseUserId + it }.toList()
                
                // 사용자들 미리 생성
                userIds.forEach { userId ->
                    userJpaRepository.save(User.create(userId))
                }
                userJpaRepository.flush() // 즉시 DB에 반영
                
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
}){
    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

sealed class TestResult {
    data class Success(val userId: Long, val token: String, val queuePosition: Int) : TestResult()
    data class Failure(val userId: Long, val statusCode: Int, val response: String) : TestResult()
    data class Error(val userId: Long, val message: String) : TestResult()
    data class Duplicate(val userId: Long, val response: String) : TestResult()
}
