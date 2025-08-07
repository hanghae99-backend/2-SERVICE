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
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class RedisTokenStoreTest : DescribeSpec({
    
    lateinit var redisTemplate: StringRedisTemplate
    lateinit var valueOperations: ValueOperations<String, String>
    lateinit var setOperations: SetOperations<String, String>
    lateinit var listOperations: ListOperations<String, String>
    lateinit var objectMapper: ObjectMapper
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
    
    describe("save") {
        context("새로운 토큰을 저장할 때") {
            it("Redis에 토큰 정보를 JSON으로 저장하고 사용자별 토큰 Set에 추가해야 한다") {
                // given
                val waitingToken = WaitingToken(
                    token = "test-token-123",
                    userId = 456L
                )
                val tokenJson = """{"token":"test-token-123","userId":456}"""
                
                every { objectMapper.writeValueAsString(waitingToken) } returns tokenJson
                every { valueOperations.set(any(), any(), any<Duration>()) } just Runs
                every { setOperations.add(any(), any()) } returns 1L
                
                // when
                redisTokenStore.save(waitingToken)
                
                // then
                verify(exactly = 1) { 
                    valueOperations.set(
                        "waiting_token:test-token-123", 
                        tokenJson, 
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
            it("JSON에서 WaitingToken 객체로 역직렬화해서 반환해야 한다") {
                // given
                val token = "test-token-123"
                val tokenJson = """{"token":"test-token-123","userId":456}"""
                val expectedToken = WaitingToken(
                    token = "test-token-123",
                    userId = 456L
                )
                
                every { valueOperations.get("waiting_token:test-token-123") } returns tokenJson
                every { objectMapper.readValue(tokenJson, WaitingToken::class.java) } returns expectedToken
                
                // when
                val result = redisTokenStore.findByToken(token)
                
                // then
                result shouldBe expectedToken
                verify(exactly = 1) { valueOperations.get("waiting_token:test-token-123") }
                verify(exactly = 1) { objectMapper.readValue(tokenJson, WaitingToken::class.java) }
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
                verify(exactly = 0) { objectMapper.readValue(any<String>(), any<Class<WaitingToken>>()) }
            }
        }
    }
    
    describe("getTokenStatus") {
        context("활성 토큰인 경우") {
            it("ACTIVE 상태를 반환해야 한다") {
                // given
                val token = "active-token"
                every { setOperations.isMember("active_tokens", token) } returns true
                
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
                every { setOperations.isMember("active_tokens", token) } returns false
                every { listOperations.indexOf("waiting_queue", token) } returns 5L
                
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
                every { setOperations.isMember("active_tokens", token) } returns false
                every { listOperations.indexOf("waiting_queue", token) } returns null
                
                // when
                val result = redisTokenStore.getTokenStatus(token)
                
                // then
                result shouldBe TokenStatus.EXPIRED
            }
        }
    }
    
    describe("activateToken") {
        context("토큰을 활성화할 때") {
            it("대기열에서 제거하고 활성 토큰 Set에 추가하며 타임스탬프를 기록해야 한다") {
                // given
                val token = "token-to-activate"
                every { listOperations.remove("waiting_queue", 0, token) } returns 1L
                every { setOperations.add("active_tokens", token) } returns 1L
                every { valueOperations.set(any(), any(), any<Duration>()) } just Runs
                every { redisTemplate.expire(any(), any<Duration>()) } returns true
                
                // when
                redisTokenStore.activateToken(token)
                
                // then
                verify(exactly = 1) { listOperations.remove("waiting_queue", 0, token) }
                verify(exactly = 1) { setOperations.add("active_tokens", token) }
                verify(exactly = 1) { 
                    valueOperations.set(
                        "active_timestamp:token-to-activate",
                        any(),
                        Duration.ofMinutes(10)
                    )
                }
                verify(exactly = 1) { redisTemplate.expire("active_tokens", Duration.ofMinutes(10)) }
            }
        }
    }
    
    describe("expireToken") {
        context("토큰을 만료시킬 때") {
            it("대기열과 활성 토큰에서 모두 제거하고 타임스탬프도 삭제해야 한다") {
                // given
                val token = "token-to-expire"
                every { listOperations.remove("waiting_queue", 0, token) } returns 1L
                every { setOperations.remove("active_tokens", token) } returns 1L
                every { redisTemplate.delete("active_timestamp:token-to-expire") } returns true
                
                // when
                redisTokenStore.expireToken(token)
                
                // then
                verify(exactly = 1) { listOperations.remove("waiting_queue", 0, token) }
                verify(exactly = 1) { setOperations.remove("active_tokens", token) }
                verify(exactly = 1) { redisTemplate.delete("active_timestamp:token-to-expire") }
            }
        }
    }
    
    describe("addToWaitingQueue") {
        context("토큰을 대기열에 추가할 때") {
            it("List의 오른쪽(뒤)에 추가해야 한다") {
                // given
                val token = "new-waiting-token"
                every { listOperations.rightPush("waiting_queue", token) } returns 1L
                
                // when
                redisTokenStore.addToWaitingQueue(token)
                
                // then
                verify(exactly = 1) { listOperations.rightPush("waiting_queue", token) }
            }
        }
    }
    
    describe("getNextTokensFromQueue") {
        context("대기열에서 다음 토큰들을 가져올 때") {
            it("요청한 개수만큼 List의 왼쪽(앞)에서 제거해서 반환해야 한다") {
                // given
                val count = 3
                every { listOperations.leftPop("waiting_queue") } returnsMany listOf("token1", "token2", "token3", null)
                
                // when
                val result = redisTokenStore.getNextTokensFromQueue(count)
                
                // then
                result shouldHaveSize 3
                result shouldContain "token1"
                result shouldContain "token2"
                result shouldContain "token3"
                verify(exactly = 3) { listOperations.leftPop("waiting_queue") }
            }
        }
        
        context("대기열이 비어있을 때") {
            it("빈 리스트를 반환해야 한다") {
                // given
                val count = 3
                every { listOperations.leftPop("waiting_queue") } returns null

                // when
                val result = redisTokenStore.getNextTokensFromQueue(count)

                // then
                result shouldHaveSize 0
                verify(exactly = 3) { listOperations.leftPop("waiting_queue") }
            }
        }
    }
    
    describe("getQueuePosition") {
        context("대기열에서 토큰의 위치를 조회할 때") {
            it("올바른 위치를 반환해야 한다") {
                // given
                val token = "waiting-token"
                every { listOperations.indexOf("waiting_queue", token) } returns 5L
                
                // when
                val result = redisTokenStore.getQueuePosition(token)
                
                // then
                result shouldBe 5
                verify(exactly = 1) { listOperations.indexOf("waiting_queue", token) }
            }
        }
        
        context("대기열에 없는 토큰을 조회할 때") {
            it("-1을 반환해야 한다") {
                // given
                val token = "non-waiting-token"
                every { listOperations.indexOf("waiting_queue", token) } returns null
                
                // when
                val result = redisTokenStore.getQueuePosition(token)
                
                // then
                result shouldBe -1
                verify(exactly = 1) { listOperations.indexOf("waiting_queue", token) }
            }
        }
    }
    
    describe("countActiveTokens") {
        context("활성 토큰 수를 조회할 때") {
            it("Set의 크기를 반환해야 한다") {
                // given
                every { setOperations.size("active_tokens") } returns 42L
                
                // when
                val result = redisTokenStore.countActiveTokens()
                
                // then
                result shouldBe 42L
                verify(exactly = 1) { setOperations.size("active_tokens") }
            }
        }
        
        context("활성 토큰이 없을 때") {
            it("0을 반환해야 한다") {
                // given
                every { setOperations.size("active_tokens") } returns null
                
                // when
                val result = redisTokenStore.countActiveTokens()
                
                // then
                result shouldBe 0L
                verify(exactly = 1) { setOperations.size("active_tokens") }
            }
        }
    }
    
    describe("findExpiredActiveTokens") {
        context("만료된 활성 토큰이 있을 때") {
            it("TTL을 초과한 토큰들을 반환해야 한다") {
                // given
                val currentTime = System.currentTimeMillis()
                val expiredTime = (currentTime - Duration.ofMinutes(15).toMillis()).toString() // 15분 전 (10분 TTL 초과)
                val validTime = (currentTime - Duration.ofMinutes(5).toMillis()).toString()   // 5분 전 (유효)
                
                every { setOperations.members("active_tokens") } returns setOf("token1", "token2", "token3")
                every { valueOperations.get("active_timestamp:token1") } returns expiredTime
                every { valueOperations.get("active_timestamp:token2") } returns validTime
                every { valueOperations.get("active_timestamp:token3") } returns null // 타임스탬프 없음
                
                // when
                val result = redisTokenStore.findExpiredActiveTokens()
                
                // then
                result shouldHaveSize 2
                result shouldContain "token1" // 15분 전 (만료)
                result shouldNotContain "token2" // 5분 전 (유효)
                result shouldContain "token3" // 타임스탬프 없음 (만료)
            }
        }
        
        context("만료된 활성 토큰이 없을 때") {
            it("빈 리스트를 반환해야 한다") {
                // given
                every { setOperations.members("active_tokens") } returns emptySet()
                
                // when
                val result = redisTokenStore.findExpiredActiveTokens()
                
                // then
                result shouldHaveSize 0
                verify(exactly = 1) { setOperations.members("active_tokens") }
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
                val tokenJson = """{"token":"token-to-delete","userId":123}"""
                
                every { valueOperations.get("waiting_token:token-to-delete") } returns tokenJson
                every { objectMapper.readValue(tokenJson, WaitingToken::class.java) } returns waitingToken
                every { redisTemplate.delete("waiting_token:token-to-delete") } returns true
                every { setOperations.remove("user_tokens:123", token) } returns 1L
                every { listOperations.remove("waiting_queue", 0, token) } returns 1L
                every { setOperations.remove("active_tokens", token) } returns 1L
                every { redisTemplate.delete("active_timestamp:token-to-delete") } returns true
                
                // when
                redisTokenStore.delete(token)
                
                // then
                verify(exactly = 1) { redisTemplate.delete("waiting_token:token-to-delete") }
                verify(exactly = 1) { setOperations.remove("user_tokens:123", token) }
                verify(exactly = 1) { listOperations.remove("waiting_queue", 0, token) }
                verify(exactly = 1) { setOperations.remove("active_tokens", token) }
                verify(exactly = 1) { redisTemplate.delete("active_timestamp:token-to-delete") }
            }
        }
    }
})