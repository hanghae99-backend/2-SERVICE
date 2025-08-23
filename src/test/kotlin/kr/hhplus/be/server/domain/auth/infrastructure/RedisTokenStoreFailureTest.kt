package kr.hhplus.be.server.domain.auth.infrastructure

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import org.springframework.dao.QueryTimeoutException
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.*

class RedisTokenStoreFailureTest : DescribeSpec({
    
    describe("RedisTokenStore 장애 상황 테스트") {
        
        lateinit var redisTemplate: RedisTemplate<String, Any>
        lateinit var valueOperations: ValueOperations<String, Any>
        lateinit var setOperations: SetOperations<String, Any>
        lateinit var zSetOperations: ZSetOperations<String, Any>
        lateinit var hashOperations: HashOperations<String, String, String>
        lateinit var objectMapper: com.fasterxml.jackson.databind.ObjectMapper
        lateinit var redisTokenStore: RedisTokenStore
        
        beforeEach {
            redisTemplate = mockk(relaxed = true)
            valueOperations = mockk(relaxed = true)
            setOperations = mockk(relaxed = true)
            zSetOperations = mockk(relaxed = true)
            hashOperations = mockk(relaxed = true)
            objectMapper = mockk(relaxed = true)
            
            every { redisTemplate.opsForValue() } returns valueOperations
            every { redisTemplate.opsForSet() } returns setOperations
            every { redisTemplate.opsForZSet() } returns zSetOperations
            every { redisTemplate.opsForHash<String, String>() } returns hashOperations
            
            redisTokenStore = RedisTokenStore(redisTemplate, objectMapper)
        }
        
        context("Redis 연결 실패 시") {
            it("토큰 저장 시 RedisConnectionFailureException이 발생해야 한다") {
                // given
                val waitingToken = WaitingToken(
                    token = "test-token",
                    userId = 123L
                )
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
                every { hashOperations.hasKey(any(), any()) } throws RedisConnectionFailureException("Redis 연결 실패")
                
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
                    userId = 123L
                )
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
                every { zSetOperations.zCard(any()) } throws 
                    QueryTimeoutException("Redis query timeout")
                
                // when & then
                shouldThrow<QueryTimeoutException> {
                    redisTokenStore.getQueueSize()
                }
            }
            
            it("활성 토큰 수 조회 시 타임아웃 예외가 발생해야 한다") {
                // given
                every { hashOperations.size(any()) } throws 
                    QueryTimeoutException("Redis query timeout")
                
                // when & then
                shouldThrow<QueryTimeoutException> {
                    redisTokenStore.countActiveTokens()
                }
            }
        }
        
        context("데이터 형변환 실패 시") {
            it("잘못된 데이터 타입 반환 시 null을 반환해야 한다") {
                // given
                val token = "test-token"
                // Redis에서 WaitingToken이 아닌 다른 타입 반환
                every { valueOperations.get(any()) } returns "invalid-data-type"
                
                // when
                val result = redisTokenStore.findByToken(token)
                
                // then - 형변환 실패 시 null 반환
                result shouldBe null
            }
        }
        
        context("Redis 명령어 실행 실패 시") {
            it("ZADD 명령어 실패 시 예외가 발생해야 한다") {
                // given
                every { valueOperations.increment(any()) } returns 1L
                every { zSetOperations.add(any(), any(), any()) } throws RuntimeException("ZADD 명령어 실행 실패")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.addToWaitingQueue("test-token")
                }
            }
            
            it("ZRANGE 명령어 실패 시 예외가 발생해야 한다") {
                // given
                every { zSetOperations.range(any(), any(), any()) } throws RuntimeException("ZRANGE 명령어 실행 실패")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.getNextTokensFromQueue(1)
                }
            }
            
            it("HSET 명령어 실패 시 예외가 발생해야 한다") {
                // given
                val token = "test-token"
                every { zSetOperations.remove(any(), any()) } returns 1L
                every { hashOperations.put(any(), any(), any()) } throws RuntimeException("HSET 명령어 실행 실패")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.activateToken(token)
                }
            }
            
            it("HDEL 명령어 실패 시 예외가 발생해야 한다") {
                // given
                val token = "test-token"
                every { zSetOperations.remove(any(), any()) } returns 1L
                every { hashOperations.delete(any(), any()) } throws RuntimeException("HDEL 명령어 실행 실패")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.expireToken(token)
                }
            }
        }
        
        context("부분적 Redis 실패 시") {
            it("일부 작업은 성공하고 일부는 실패할 수 있어야 한다") {
                // given
                val tokens = mutableSetOf("token1", "token2", "token3")
                every { zSetOperations.range(any(), any(), any()) } returns tokens
                every { zSetOperations.remove(any(), any()) } returns 1L
                
                // when
                val result = redisTokenStore.getNextTokensFromQueue(3)
                
                // then - 모든 토큰을 성공적으로 가져와야 함
                result.size shouldBe 3
                result shouldContain "token1"
                result shouldContain "token2"
                result shouldContain "token3"
            }
            
            it("Hash 읽기는 실패하지만 ZSet 읽기는 성공할 수 있어야 한다") {
                // given
                val token = "test-token"
                every { hashOperations.hasKey(any(), any()) } throws RuntimeException("Hash 읽기 실패")
                every { zSetOperations.rank(any(), any()) } returns 5L
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.getTokenStatus(token)
                }
            }
        }
        
        context("Redis 복구 시나리오") {
            it("연결 실패 후 재연결이 가능해야 한다") {
                // given
                val token = "recovery-test-token"
                val waitingToken = WaitingToken(
                    token = token,
                    userId = 123L
                )
                var callCount = 0
                
                // 첫 번째 호출: 실패, 두 번째 호출: 성공
                every { valueOperations.get("waiting_token:$token") } answers {
                    if (callCount++ == 0) {
                        throw RedisConnectionFailureException("연결 실패")
                    } else {
                        waitingToken
                    }
                }
                
                // when & then - 첫 번째 호출은 실패
                shouldThrow<RedisConnectionFailureException> {
                    redisTokenStore.findByToken(token)
                }
                
                // when - 두 번째 호출은 성공 (재연결됨)
                val result = redisTokenStore.findByToken(token)
                
                // then
                result shouldBe waitingToken
                result?.token shouldBe token
            }
            
            it("Hash 작업 실패 후 재시도가 가능해야 한다") {
                // given
                val token = "hash-recovery-token"
                var callCount = 0
                
                // 첫 번째 호출: 실패, 두 번째 호출: 성공
                every { hashOperations.hasKey("active_tokens_hash", token) } answers {
                    if (callCount++ == 0) {
                        throw RedisConnectionFailureException("Hash 연결 실패")
                    } else {
                        true
                    }
                }
                
                // when & then - 첫 번째 호출은 실패
                shouldThrow<RedisConnectionFailureException> {
                    redisTokenStore.isTokenActive(token)
                }
                
                // when & then - 두 번째 호출은 성공
                val result = redisTokenStore.isTokenActive(token)
                result shouldBe true
            }
        }
        
        context("Hash 만료 시간 처리 실패") {
            it("만료된 활성 토큰 조회 시 Hash 읽기 실패가 발생해야 한다") {
                // given
                every { hashOperations.entries(any()) } throws RuntimeException("Hash entries 읽기 실패")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.findExpiredActiveTokens()
                }
            }
            
            it("잘못된 만료 시간 형식에 대해 안전하게 처리해야 한다") {
                // given
                val entries = mapOf(
                    "token1" to "invalid-time",
                    "token2" to "not-a-number"
                )
                every { hashOperations.entries("active_tokens_hash") } returns entries
                
                // when
                val result = redisTokenStore.findExpiredActiveTokens()
                
                // then - 잘못된 시간 형식의 토큰들은 만료된 것으로 처리
                result.size shouldBe 2
                result shouldContain "token1"
                result shouldContain "token2"
            }
        }
        
        context("트랜잭션 실패 시나리오") {
            it("save 작업 중 일부만 성공하면 부분적인 상태가 될 수 있다") {
                // given
                val waitingToken = WaitingToken(
                    token = "partial-save-token",
                    userId = 456L
                )
                
                // ValueOperations는 성공, SetOperations는 실패
                every { valueOperations.set(any(), any(), any<java.time.Duration>()) } just Runs
                every { setOperations.add(any(), any()) } throws RuntimeException("Set 추가 실패")
                
                // when & then
                shouldThrow<RuntimeException> {
                    redisTokenStore.save(waitingToken)
                }
                
                // 첫 번째 작업은 수행되었지만 두 번째 작업에서 실패
                verify(exactly = 1) { 
                    valueOperations.set(
                        "waiting_token:partial-save-token", 
                        waitingToken, 
                        java.time.Duration.ofMinutes(30)
                    )
                }
            }
        }
        
        context("null 처리 시나리오") {
            it("ZSet rank가 null일 때 적절히 처리해야 한다") {
                // given
                val token = "null-rank-token"
                every { zSetOperations.rank("waiting_queue_zset", token) } returns null
                
                // when
                val position = redisTokenStore.getQueuePosition(token)
                
                // then
                position shouldBe -1
            }
            
            it("ZSet zCard가 null일 때 0을 반환해야 한다") {
                // given
                every { zSetOperations.zCard("waiting_queue_zset") } returns null
                
                // when
                val size = redisTokenStore.getQueueSize()
                
                // then
                size shouldBe 0L
            }
        }
    }
})
