package kr.hhplus.be.server.domain.auth.infrastructure

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDateTime

class RedisFailureTest : DescribeSpec({
    
    describe("Redis 장애 상황 테스트") {
        
        lateinit var redisTemplate: StringRedisTemplate
        lateinit var valueOperations: ValueOperations<String, String>
        lateinit var setOperations: SetOperations<String, String>
        lateinit var listOperations: ListOperations<String, String>
        lateinit var objectMapper: com.fasterxml.jackson.databind.ObjectMapper
        lateinit var redisTokenStore: RedisTokenStore
        
        beforeEach {
            redisTemplate = mockk(relaxed = true)
            valueOperations = mockk(relaxed = true)
            setOperations = mockk(relaxed = true)
            listOperations = mockk(relaxed = true)
            objectMapper = mockk(relaxed = true)
            
            every { redisTemplate.opsForValue() } returns valueOperations
            every { redisTemplate.opsForSet() } returns setOperations
            every { redisTemplate.opsForList() } returns listOperations
            
            redisTokenStore = RedisTokenStore(redisTemplate, objectMapper)
        }
        
        context("Redis 연결 실패 시") {
            it("토큰 저장 시 RedisConnectionFailureException이 발생해야 한다") {
                // given
                val waitingToken = WaitingToken(
                    token = "test-token",
                    userId = "user-123",
                    createdAt = LocalDateTime.now()
                )
                every { objectMapper.writeValueAsString(any()) } returns "{}"
                every { valueOperations.set(any(), any(), any<java.time.Duration>()) } throws RedisConnectionFailureException("Redis 연결 실패")
                
                // when & then
                shouldThrow<RedisConnectionFailureException> {
                    redisTokenStore.save(waitingToken)
                }
            }
            
            it("토큰 조회 시 RedisConnectionFailureException이 발생해야 한다") {
                // given
                val token = "test-token"
                every { valueOperations.get(any()) } throws RedisConnectionFailureException("Redis 연결 실패")
                
                // when & then
                shouldThrow<RedisConnectionFailureException> {
                    redisTokenStore.findByToken(token)
                }
            }
            
            it("토큰 상태 조회 시 RedisConnectionFailureException이 발생해야 한다") {
                // given
                val token = "test-token"
                every { setOperations.isMember(any(), any()) } throws RedisConnectionFailureException("Redis 연결 실패")
                
                // when & then
                shouldThrow<RedisConnectionFailureException> {
                    redisTokenStore.getTokenStatus(token)
                }
            }
        }
        
        context("Redis 메모리 부족 시") {
            it("토큰 저장 시 적절한 예외가 발생해야 한다") {
                // given
                val waitingToken = WaitingToken(
                    token = "test-token",
                    userId = "user-123",
                    createdAt = LocalDateTime.now()
                )
                every { objectMapper.writeValueAsString(any()) } returns "{}"
                every { valueOperations.set(any(), any(), any<java.time.Duration>()) } throws 
                    RuntimeException("OOM command not allowed when used memory > 'maxmemory'")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.save(waitingToken)
                }
            }
        }
        
        context("Redis 타임아웃 시") {
            it("대기열 조회 시 타임아웃 예외가 발생해야 한다") {
                // given
                every { listOperations.size(any()) } throws 
                    org.springframework.dao.QueryTimeoutException("Redis query timeout")
                
                // when & then
                shouldThrow<org.springframework.dao.QueryTimeoutException> {
                    redisTokenStore.getQueueSize()
                }
            }
        }
        
        context("데이터 직렬화/역직렬화 실패 시") {
            it("JSON 직렬화 실패 시 예외가 발생해야 한다") {
                // given
                val waitingToken = WaitingToken(
                    token = "test-token",
                    userId = "user-123",
                    createdAt = LocalDateTime.now()
                )
                every { objectMapper.writeValueAsString(any()) } throws 
                    com.fasterxml.jackson.core.JsonProcessingException("JSON 직렬화 실패")
                
                // when & then
                shouldThrow<com.fasterxml.jackson.core.JsonProcessingException> {
                    redisTokenStore.save(waitingToken)
                }
            }
            
            it("JSON 역직렬화 실패 시 예외가 발생해야 한다") {
                // given
                val token = "test-token"
                val invalidJson = "invalid json"
                every { valueOperations.get(any()) } returns invalidJson
                every { objectMapper.readValue(any<String>(), any<Class<WaitingToken>>()) } throws 
                    com.fasterxml.jackson.core.JsonParseException(null, "JSON 파싱 실패")
                
                // when & then
                shouldThrow<com.fasterxml.jackson.core.JsonParseException> {
                    redisTokenStore.findByToken(token)
                }
            }
        }
        
        context("Redis 명령어 실행 실패 시") {
            it("SET 명령어 실패 시 예외가 발생해야 한다") {
                // given
                every { setOperations.add(any(), any()) } throws RuntimeException("SET 명령어 실행 실패")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.addToWaitingQueue("test-token")
                }
            }
            
            it("LPOP 명령어 실패 시 예외가 발생해야 한다") {
                // given
                every { listOperations.leftPop(any()) } throws RuntimeException("LPOP 명령어 실행 실패")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.getNextTokensFromQueue(1)
                }
            }
        }
        
        context("부분적 Redis 실패 시") {
            it("일부 작업은 성공하고 일부는 실패할 수 있어야 한다") {
                // given
                val tokens = listOf("token1", "token2", "token3")
                
                every { listOperations.leftPop("waiting_queue") } returnsMany listOf(
                    "token1",  // 첫 번째는 성공
                    null       // 두 번째부터는 실패 (빈 큐)
                ) andThenThrows RuntimeException("연결 실패")  // 세 번째는 예외
                
                // when
                val result = redisTokenStore.getNextTokensFromQueue(3)
                
                // then - 첫 번째 토큰만 성공적으로 가져와야 함
                result.size shouldBe 1
                result[0] shouldBe "token1"
            }
        }
        
        context("Redis 복구 시나리오") {
            it("연결 실패 후 재연결이 가능해야 한다") {
                // given
                val token = "recovery-test-token"
                
                // 첫 번째 호출: 실패
                every { valueOperations.get("waiting_token:$token") } throws RedisConnectionFailureException("연결 실패") andThen "token-data"
                
                // when & then - 첫 번째 호출은 실패
                shouldThrow<RedisConnectionFailureException> {
                    redisTokenStore.findByToken(token)
                }
                
                // when & then - 두 번째 호출은 성공 (재연결됨)
                every { objectMapper.readValue(any<String>(), any<Class<WaitingToken>>()) } returns WaitingToken(
                    token = token,
                    userId = "user-123",
                    createdAt = LocalDateTime.now()
                )
                
                val result = redisTokenStore.findByToken(token)
                result?.token shouldBe token
            }
        }
    }
})