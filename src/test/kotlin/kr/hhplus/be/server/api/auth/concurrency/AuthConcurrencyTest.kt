package kr.hhplus.be.server.api.auth.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.config.ConcurrencyTest
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.models.User
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ConcurrencyTest
class AuthConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val userJpaRepository: UserJpaRepository,
    private val objectMapper: ObjectMapper,
) : DescribeSpec({

    extension(SpringExtension)

    lateinit var mockMvc: MockMvc

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }

    describe("토큰 발급 동시성 테스트") {
        context("여러 사용자가 동시에 토큰 발급을 요청할 때") {
            it("모든 요청이 안전하게 처리되어야 한다") {
                // given
                val userCount = 5
                val baseUserId = System.currentTimeMillis()
                val userIds = (0 until userCount).map { baseUserId + it }.toList()
                
                // 사용자들 미리 생성
                userIds.forEach { userId ->
                    userJpaRepository.save(User.createWithId(userId))
                }
                userJpaRepository.flush()
                
                val executor = Executors.newFixedThreadPool(userCount)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when - 동시 요청
                val futures = userIds.map { userId ->
                    CompletableFuture.supplyAsync({
                        try {
                            val request = TokenIssueRequest(userId)
                            val result = mockMvc.perform(
                                post("/api/v1/tokens")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()
                            
                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                "SUCCESS"
                            } else {
                                failureCount.incrementAndGet()
                                "FAILURE"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR"
                        }
                    }, executor)
                }
                
                // 모든 요청 완료 대기
                futures.forEach { it.get(10, TimeUnit.SECONDS) }
                
                // then - 검증
                successCount.get() shouldBeGreaterThan 0
                println("성공 수: ${successCount.get()}, 실패 수: ${failureCount.get()}")
                
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("동일한 사용자가 동시에 여러 번 토큰 발급을 요청할 때") {
            it("중복 토큰 발급을 방지해야 한다") {
                // given
                val userId = System.currentTimeMillis() + 1000
                val user = User.createWithId(userId)
                userJpaRepository.save(user)
                userJpaRepository.flush()

                val requestCount = 3
                val executor = Executors.newFixedThreadPool(requestCount)
                val successCount = AtomicInteger(0)
                val duplicateCount = AtomicInteger(0)

                // when - 동일한 사용자로 동시 요청
                val futures = (0 until requestCount).map { _ ->
                    CompletableFuture.supplyAsync({
                        try {
                            val request = TokenIssueRequest(userId)
                            val result = mockMvc.perform(
                                post("/api/v1/tokens")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            when (result.response.status) {
                                201 -> {
                                    val body = result.response.contentAsString
                                    val responseJson = objectMapper.readTree(body)
                                    val message = responseJson.get("data")?.get("message")?.asText() ?: ""
                                    
                                    if (message.contains("이미 대기열에 등록된")) {
                                        duplicateCount.incrementAndGet()
                                        "DUPLICATE"
                                    } else {
                                        successCount.incrementAndGet()
                                        "SUCCESS"
                                    }
                                }
                                409 -> {
                                    duplicateCount.incrementAndGet()
                                    "DUPLICATE"
                                }
                                else -> "FAILURE"
                            }
                        } catch (e: Exception) {
                            "ERROR"
                        }
                    }, executor)
                }
                
                // 모든 요청 완료 대기
                futures.forEach { it.get(10, TimeUnit.SECONDS) }

                // then - 검증
                println("성공 수: ${successCount.get()}, 중복 수: ${duplicateCount.get()}")
                
                // 최소 1개의 성공적인 토큰 발급이 있어야 함
                successCount.get() shouldBe 1
                
                // 나머지는 모두 중복 처리되어야 함
                (successCount.get() + duplicateCount.get()) shouldBe requestCount

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})