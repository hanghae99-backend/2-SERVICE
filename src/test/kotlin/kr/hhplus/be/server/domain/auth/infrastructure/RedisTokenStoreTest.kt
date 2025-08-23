package kr.hhplus.be.server.domain.auth.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.mockk.*
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.ZSetOperations
import java.time.Duration

class RedisTokenStoreTest : DescribeSpec({
    
    lateinit var redisTemplate: RedisTemplate<String, Any>
    lateinit var valueOperations: ValueOperations<String, Any>
    lateinit var setOperations: SetOperations<String, Any>
    lateinit var zSetOperations: ZSetOperations<String, Any>
    lateinit var hashOperations: HashOperations<String, String, String>
    lateinit var objectMapper: ObjectMapper
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
    
    describe("save") {
        context("새로운 토큰을 저장할 때") {
            it("Redis에 토큰 정보를 저장하고 사용자별 토큰 Set에 추가해야 한다") {
                // given
                val waitingToken = WaitingToken(
                    token = "test-token-123",
                    userId = 456L
                )
                
                every { valueOperations.set(any(), any(), any<Duration>()) } just Runs
                every { setOperations.add(any(), any()) } returns 1L
                
                // when
                redisTokenStore.save(waitingToken)
                
                // then
                verify(exactly = 1) { 
                    valueOperations.set(
                        "waiting_token:test-token-123", 
                        waitingToken, 
                        Duration.ofMinutes(30)
                    )
                }
                verify(exactly = 1) { 
                    setOperations.add("user_tokens:456", "test-token-123")
                }
            }
        }
    }
    
    describe("findByToken") {
        context("존재하는 토큰을 조회할 때") {
            it("WaitingToken 객체를 반환해야 한다") {
                // given
                val token = "test-token-123"
                val expectedToken = WaitingToken(
                    token = "test-token-123",
                    userId = 456L
                )
                
                every { valueOperations.get("waiting_token:test-token-123") } returns expectedToken
                
                // when
                val result = redisTokenStore.findByToken(token)
                
                // then
                result shouldBe expectedToken
                verify(exactly = 1) { valueOperations.get("waiting_token:test-token-123") }
            }
        }
        
        context("존재하지 않는 토큰을 조회할 때") {
            it("null을 반환해야 한다") {
                // given
                val token = "non-existent-token"
                every { valueOperations.get("waiting_token:non-existent-token") } returns null
                
                // when
                val result = redisTokenStore.findByToken(token)
                
                // then
                result shouldBe null
                verify(exactly = 1) { valueOperations.get("waiting_token:non-existent-token") }
            }
        }
    }
    
    describe("getTokenStatus") {
        context("활성 토큰인 경우") {
            it("ACTIVE 상태를 반환해야 한다") {
                // given
                val token = "active-token"
                every { hashOperations.hasKey("active_tokens_hash", token) } returns true
                
                // when
                val result = redisTokenStore.getTokenStatus(token)
                
                // then
                result shouldBe TokenStatus.ACTIVE
            }
        }
        
        context("대기 중인 토큰인 경우") {
            it("WAITING 상태를 반환해야 한다") {
                // given
                val token = "waiting-token"
                every { hashOperations.hasKey("active_tokens_hash", token) } returns false
                every { zSetOperations.rank("waiting_queue_zset", token) } returns 5L
                
                // when
                val result = redisTokenStore.getTokenStatus(token)
                
                // then
                result shouldBe TokenStatus.WAITING
            }
        }
        
        context("만료된 토큰인 경우") {
            it("EXPIRED 상태를 반환해야 한다") {
                // given
                val token = "expired-token"
                every { hashOperations.hasKey("active_tokens_hash", token) } returns false
                every { zSetOperations.rank("waiting_queue_zset", token) } returns null
                
                // when
                val result = redisTokenStore.getTokenStatus(token)
                
                // then
                result shouldBe TokenStatus.EXPIRED
            }
        }
    }
    
    describe("activateToken") {
        context("토큰을 활성화할 때") {
            it("대기열에서 제거하고 활성 토큰 Hash에 추가해야 한다") {
                // given
                val token = "token-to-activate"
                every { zSetOperations.remove("waiting_queue_zset", token) } returns 1L
                every { hashOperations.put("active_tokens_hash", token, any()) } just Runs
                
                // when
                redisTokenStore.activateToken(token)
                
                // then
                verify(exactly = 1) { zSetOperations.remove("waiting_queue_zset", token) }
                verify(exactly = 1) { 
                    hashOperations.put("active_tokens_hash", token, any())
                }
            }
        }
    }
    
    describe("expireToken") {
        context("토큰을 만료시킬 때") {
            it("대기열과 활성 토큰 Hash에서 모두 제거해야 한다") {
                // given
                val token = "token-to-expire"
                every { zSetOperations.remove("waiting_queue_zset", token) } returns 1L
                every { hashOperations.delete("active_tokens_hash", token) } returns 1L
                
                // when
                redisTokenStore.expireToken(token)
                
                // then
                verify(exactly = 1) { zSetOperations.remove("waiting_queue_zset", token) }
                verify(exactly = 1) { hashOperations.delete("active_tokens_hash", token) }
            }
        }
    }
    
    describe("addToWaitingQueue") {
        context("토큰을 대기열에 추가할 때") {
            it("ZSet에 순서대로 추가해야 한다") {
                // given
                val token = "new-waiting-token"
                every { valueOperations.increment("queue_sequence") } returns 5L
                every { zSetOperations.add("waiting_queue_zset", token, 5.0) } returns true
                
                // when
                redisTokenStore.addToWaitingQueue(token)
                
                // then
                verify(exactly = 1) { valueOperations.increment("queue_sequence") }
                verify(exactly = 1) { zSetOperations.add("waiting_queue_zset", token, 5.0) }
            }
        }
    }
    
    describe("getNextTokensFromQueue") {
        context("대기열에서 다음 토큰들을 가져올 때") {
            it("요청한 개수만큼 ZSet의 앞에서 제거해서 반환해야 한다") {
                // given
                val count = 3
                val tokens = setOf("token1", "token2", "token3")
                every { zSetOperations.range("waiting_queue_zset", 0, 2) } returns tokens
                every { zSetOperations.remove("waiting_queue_zset", "token1") } returns 1L
                every { zSetOperations.remove("waiting_queue_zset", "token2") } returns 1L
                every { zSetOperations.remove("waiting_queue_zset", "token3") } returns 1L
                
                // when
                val result = redisTokenStore.getNextTokensFromQueue(count)
                
                // then
                result shouldHaveSize 3
                result shouldContain "token1"
                result shouldContain "token2"
                result shouldContain "token3"
                verify(exactly = 1) { zSetOperations.range("waiting_queue_zset", 0, 2) }
                verify(exactly = 1) { zSetOperations.remove("waiting_queue_zset", "token1") }
                verify(exactly = 1) { zSetOperations.remove("waiting_queue_zset", "token2") }
                verify(exactly = 1) { zSetOperations.remove("waiting_queue_zset", "token3") }
            }
        }
        
        context("대기열이 비어있을 때") {
            it("빈 리스트를 반환해야 한다") {
                // given
                val count = 3
                every { zSetOperations.range("waiting_queue_zset", 0, 2) } returns emptySet()

                // when
                val result = redisTokenStore.getNextTokensFromQueue(count)

                // then
                result shouldHaveSize 0
                verify(exactly = 1) { zSetOperations.range("waiting_queue_zset", 0, 2) }
            }
        }
    }
    
    describe("getQueuePosition") {
        context("대기열에서 토큰의 위치를 조회할 때") {
            it("올바른 위치를 반환해야 한다") {
                // given
                val token = "waiting-token"
                every { zSetOperations.rank("waiting_queue_zset", token) } returns 5L
                
                // when
                val result = redisTokenStore.getQueuePosition(token)
                
                // then
                result shouldBe 5
                verify(exactly = 1) { zSetOperations.rank("waiting_queue_zset", token) }
            }
        }
        
        context("대기열에 없는 토큰을 조회할 때") {
            it("-1을 반환해야 한다") {
                // given
                val token = "non-waiting-token"
                every { zSetOperations.rank("waiting_queue_zset", token) } returns null
                
                // when
                val result = redisTokenStore.getQueuePosition(token)
                
                // then
                result shouldBe -1
                verify(exactly = 1) { zSetOperations.rank("waiting_queue_zset", token) }
            }
        }
    }
    
    describe("countActiveTokens") {
        context("활성 토큰 수를 조회할 때") {
            it("Hash의 크기를 반환해야 한다") {
                // given
                every { hashOperations.size("active_tokens_hash") } returns 42L
                
                // when
                val result = redisTokenStore.countActiveTokens()
                
                // then
                result shouldBe 42L
                verify(exactly = 1) { hashOperations.size("active_tokens_hash") }
            }
        }
        
        context("활성 토큰이 없을 때") {
            it("0을 반환해야 한다") {
                // given
                every { hashOperations.size("active_tokens_hash") } returns 0L
                
                // when
                val result = redisTokenStore.countActiveTokens()
                
                // then
                result shouldBe 0L
                verify(exactly = 1) { hashOperations.size("active_tokens_hash") }
            }
        }
    }
    
    describe("findExpiredActiveTokens") {
        context("만료된 활성 토큰이 있을 때") {
            it("만료 시간을 초과한 토큰들을 반환해야 한다") {
                // given
                val currentTime = System.currentTimeMillis()
                val expiredTime = (currentTime - Duration.ofMinutes(15).toMillis()).toString() // 15분 전 (만료)
                val validTime = (currentTime + Duration.ofMinutes(5).toMillis()).toString()   // 5분 후 (유효)
                
                val entries = mapOf(
                    "token1" to expiredTime,
                    "token2" to validTime,
                    "token3" to "invalid-time"
                )
                
                every { hashOperations.entries("active_tokens_hash") } returns entries
                
                // when
                val result = redisTokenStore.findExpiredActiveTokens()
                
                // then
                result shouldHaveSize 2
                result shouldContain "token1" // 만료됨
                result shouldNotContain "token2" // 유효함
                result shouldContain "token3" // 유효하지 않은 시간 형식 (만료로 처리)
                verify(exactly = 1) { hashOperations.entries("active_tokens_hash") }
            }
        }
        
        context("만료된 활성 토큰이 없을 때") {
            it("빈 리스트를 반환해야 한다") {
                // given
                every { hashOperations.entries("active_tokens_hash") } returns emptyMap()
                
                // when
                val result = redisTokenStore.findExpiredActiveTokens()
                
                // then
                result shouldHaveSize 0
                verify(exactly = 1) { hashOperations.entries("active_tokens_hash") }
            }
        }
    }
    
    describe("validate") {
        context("유효한 토큰인 경우") {
            it("true를 반환해야 한다") {
                // given
                val token = "valid-token"
                every { redisTemplate.hasKey("waiting_token:valid-token") } returns true
                
                // when
                val result = redisTokenStore.validate(token)
                
                // then
                result shouldBe true
                verify(exactly = 1) { redisTemplate.hasKey("waiting_token:valid-token") }
            }
        }
        
        context("유효하지 않은 토큰인 경우") {
            it("false를 반환해야 한다") {
                // given
                val token = "invalid-token"
                every { redisTemplate.hasKey("waiting_token:invalid-token") } returns false
                
                // when
                val result = redisTokenStore.validate(token)
                
                // then
                result shouldBe false
                verify(exactly = 1) { redisTemplate.hasKey("waiting_token:invalid-token") }
            }
        }
    }
    
    describe("delete") {
        context("토큰을 삭제할 때") {
            it("모든 관련 데이터를 삭제해야 한다") {
                // given
                val token = "token-to-delete"
                val waitingToken = WaitingToken(
                    token = token,
                    userId = 123L
                )
                
                every { valueOperations.get("waiting_token:token-to-delete") } returns waitingToken
                every { redisTemplate.delete("waiting_token:token-to-delete") } returns true
                every { setOperations.remove("user_tokens:123", token) } returns 1L
                every { zSetOperations.remove("waiting_queue_zset", token) } returns 1L
                every { hashOperations.delete("active_tokens_hash", token) } returns 1L
                
                // when
                redisTokenStore.delete(token)
                
                // then
                verify(exactly = 1) { redisTemplate.delete("waiting_token:token-to-delete") }
                verify(exactly = 1) { setOperations.remove("user_tokens:123", token) }
                verify(exactly = 1) { zSetOperations.remove("waiting_queue_zset", token) }
                verify(exactly = 1) { hashOperations.delete("active_tokens_hash", token) }
            }
        }
    }
    
    describe("findActiveTokenByUserId") {
        context("사용자의 활성 토큰이 있을 때") {
            it("활성 토큰을 반환해야 한다") {
                // given
                val userId = 123L
                val activeToken = "active-token"
                val inactiveToken = "inactive-token"
                val userTokens = setOf(activeToken, inactiveToken)
                val waitingToken = WaitingToken(token = activeToken, userId = userId)
                
                every { setOperations.members("user_tokens:123") } returns userTokens
                every { hashOperations.hasKey("active_tokens_hash", activeToken) } returns true
                every { hashOperations.hasKey("active_tokens_hash", inactiveToken) } returns false
                every { valueOperations.get("waiting_token:active-token") } returns waitingToken
                
                // when
                val result = redisTokenStore.findActiveTokenByUserId(userId)
                
                // then
                result shouldBe waitingToken
                verify(exactly = 1) { setOperations.members("user_tokens:123") }
                verify(exactly = 1) { hashOperations.hasKey("active_tokens_hash", activeToken) }
            }
        }
        
        context("사용자의 활성 토큰이 없을 때") {
            it("null을 반환해야 한다") {
                // given
                val userId = 123L
                every { setOperations.members("user_tokens:123") } returns null
                
                // when
                val result = redisTokenStore.findActiveTokenByUserId(userId)
                
                // then
                result shouldBe null
                verify(exactly = 1) { setOperations.members("user_tokens:123") }
            }
        }
    }
    
    describe("isTokenActive") {
        context("활성 토큰인 경우") {
            it("true를 반환해야 한다") {
                // given
                val token = "active-token"
                every { hashOperations.hasKey("active_tokens_hash", token) } returns true
                
                // when
                val result = redisTokenStore.isTokenActive(token)
                
                // then
                result shouldBe true
                verify(exactly = 1) { hashOperations.hasKey("active_tokens_hash", token) }
            }
        }
        
        context("비활성 토큰인 경우") {
            it("false를 반환해야 한다") {
                // given
                val token = "inactive-token"
                every { hashOperations.hasKey("active_tokens_hash", token) } returns false
                
                // when
                val result = redisTokenStore.isTokenActive(token)
                
                // then
                result shouldBe false
                verify(exactly = 1) { hashOperations.hasKey("active_tokens_hash", token) }
            }
        }
    }
    
    describe("isTokenInQueue") {
        context("대기열에 있는 토큰인 경우") {
            it("true를 반환해야 한다") {
                // given
                val token = "queued-token"
                every { zSetOperations.rank("waiting_queue_zset", token) } returns 5L
                
                // when
                val result = redisTokenStore.isTokenInQueue(token)
                
                // then
                result shouldBe true
                verify(exactly = 1) { zSetOperations.rank("waiting_queue_zset", token) }
            }
        }
        
        context("대기열에 없는 토큰인 경우") {
            it("false를 반환해야 한다") {
                // given
                val token = "not-queued-token"
                every { zSetOperations.rank("waiting_queue_zset", token) } returns null
                
                // when
                val result = redisTokenStore.isTokenInQueue(token)
                
                // then
                result shouldBe false
                verify(exactly = 1) { zSetOperations.rank("waiting_queue_zset", token) }
            }
        }
    }
    
    describe("getQueueSize") {
        context("대기열 크기를 조회할 때") {
            it("ZSet의 크기를 반환해야 한다") {
                // given
                every { zSetOperations.zCard("waiting_queue_zset") } returns 10L
                
                // when
                val result = redisTokenStore.getQueueSize()
                
                // then
                result shouldBe 10L
                verify(exactly = 1) { zSetOperations.zCard("waiting_queue_zset") }
            }
        }
        
        context("대기열이 비어있을 때") {
            it("0을 반환해야 한다") {
                // given
                every { zSetOperations.zCard("waiting_queue_zset") } returns null
                
                // when
                val result = redisTokenStore.getQueueSize()
                
                // then
                result shouldBe 0L
                verify(exactly = 1) { zSetOperations.zCard("waiting_queue_zset") }
            }
        }
    }
    
    describe("getTokenStatusAndPosition") {
        context("대기 중인 토큰인 경우") {
            it("WAITING 상태와 위치를 반환해야 한다") {
                // given
                val token = "waiting-token"
                every { hashOperations.hasKey("active_tokens_hash", token) } returns false
                every { zSetOperations.rank("waiting_queue_zset", token) } returns 5L
                
                // when
                val (status, position) = redisTokenStore.getTokenStatusAndPosition(token)
                
                // then
                status shouldBe TokenStatus.WAITING
                position shouldBe 5
            }
        }
        
        context("활성 토큰인 경우") {
            it("ACTIVE 상태와 null 위치를 반환해야 한다") {
                // given
                val token = "active-token"
                every { hashOperations.hasKey("active_tokens_hash", token) } returns true
                
                // when
                val (status, position) = redisTokenStore.getTokenStatusAndPosition(token)
                
                // then
                status shouldBe TokenStatus.ACTIVE
                position shouldBe null
            }
        }
    }
})
