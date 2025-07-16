package kr.hhplus.be.server.auth.store

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.*
import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.auth.WaitingToken
import kr.hhplus.be.server.auth.TokenStatus
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.SetOperations
import java.time.Duration

class RedisTokenStoreUnitTest : BehaviorSpec({
    lateinit var redisTemplate: StringRedisTemplate
    lateinit var objectMapper: ObjectMapper
    lateinit var listOps: ListOperations<String, String>
    lateinit var valueOps: ValueOperations<String, String>
    lateinit var setOps: SetOperations<String, String>
    lateinit var redisTokenStore: RedisTokenStore

    beforeTest {
        redisTemplate = mockk()
        objectMapper = mockk()
        listOps = mockk()
        valueOps = mockk()
        setOps = mockk()
        
        every { redisTemplate.opsForList() } returns listOps
        every { redisTemplate.opsForValue() } returns valueOps
        every { redisTemplate.opsForSet() } returns setOps
        
        redisTokenStore = RedisTokenStore(redisTemplate, objectMapper)
        
        clearMocks(redisTemplate, objectMapper, listOps, valueOps, setOps, answers = false, recordedCalls = true)
    }

    given("RedisTokenStore - 콘서트 예약 기본 CRUD") {
        `when`("콘서트 토큰을 저장할 때") {
            then("Redis에 정상적으로 저장된다") {
                // given
                val token = WaitingToken("concert-token-123", 1L)
                val tokenJson = """{"token":"concert-token-123","userId":1}"""
                
                every { objectMapper.writeValueAsString(token) } returns tokenJson
                every { valueOps.set(any(), any(), any<Duration>()) } just Runs
                every { setOps.add(any<String>(), any<String>()) } returns 1L

                // when
                redisTokenStore.save(token)

                // then
                verify(exactly = 1) { valueOps.set("waiting_token:concert-token-123", tokenJson, any<Duration>()) }
                verify(exactly = 1) { setOps.add("user_tokens:1", "concert-token-123") }
            }
        }
        `when`("콘서트 토큰을 조회할 때") {
            then("정상적으로 토큰이 반환된다") {
                // given
                val tokenJson = """{"token":"concert-token-123","userId":1}"""
                val expectedToken = WaitingToken("concert-token-123", 1L)
                
                every { valueOps.get("waiting_token:concert-token-123") } returns tokenJson
                every { objectMapper.readValue(tokenJson, WaitingToken::class.java) } returns expectedToken

                // when
                val result = redisTokenStore.findByToken("concert-token-123")

                // then
                result.shouldNotBeNull()
                result.token shouldBe "concert-token-123"
                result.userId shouldBe 1L
            }
        }
    }

    given("RedisTokenStore - 콘서트 예약 상태 관리") {
        `when`("콘서트 예약 중인 ACTIVE 토큰의 상태를 조회할 때") {
            then("ACTIVE가 반환된다") {
                // given
                val token = "concert-active-token"
                every { setOps.isMember("active_tokens", token) } returns true

                // when
                val status = redisTokenStore.getTokenStatus(token)

                // then
                status shouldBe TokenStatus.ACTIVE
            }
        }
        `when`("콘서트 토큰을 활성화할 때 (예약 권한 부여)") {
            then("대기열에서 제거되고 활성 토큰으로 이동하며 타임스탬프가 기록된다") {
                // given
                val token = "concert-token-to-activate"
                every { listOps.remove("waiting_queue", 0, token) } returns 1L
                every { setOps.add("active_tokens", token) } returns 1L
                every { valueOps.set(any(), any(), any<Duration>()) } just Runs
                every { redisTemplate.expire("active_tokens", any<Duration>()) } returns true

                // when
                redisTokenStore.activateToken(token)

                // then
                verify(exactly = 1) { listOps.remove("waiting_queue", 0, token) }
                verify(exactly = 1) { setOps.add("active_tokens", token) }
                verify(exactly = 1) { valueOps.set(eq("active_timestamp:$token"), any(), any<Duration>()) }
                verify(exactly = 1) { redisTemplate.expire("active_tokens", any<Duration>()) }
            }
        }
    }

    given("RedisTokenStore - 콘서트 예약 대기열 관리") {
        `when`("콘서트 대기열에 토큰을 추가할 때") {
            then("Redis List 오른쪽에 추가된다 (FIFO 순서)") {
                // given
                val token = "concert-waiting-token"
                every { listOps.rightPush("waiting_queue", token) } returns 1L

                // when
                redisTokenStore.addToWaitingQueue(token)

                // then
                verify(exactly = 1) { listOps.rightPush("waiting_queue", token) }
            }
        }
        `when`("콘서트 대기열에서 다음 사용자들을 가져올 때") {
            then("FIFO 순서로 지정된 개수만큼 반환된다") {
                // given
                val count = 5 // 5명 동시 활성화
                val tokens = listOf("user1", "user2", "user3", "user4", "user5")
                every { listOps.leftPop("waiting_queue") } returnsMany tokens.map { it } andThen null

                // when
                val result = redisTokenStore.getNextTokensFromQueue(count)

                // then
                result shouldContainExactly tokens
                verify(exactly = count) { listOps.leftPop("waiting_queue") }
            }
        }
        `when`("콘서트 대기열 크기를 조회할 때") {
            then("전체 대기자 수가 반환된다") {
                // given
                val expectedSize = 5000L // 5000명 대기
                every { listOps.size("waiting_queue") } returns expectedSize

                // when
                val size = redisTokenStore.getQueueSize()

                // then
                size shouldBe expectedSize
            }
        }
    }

    given("RedisTokenStore - 콘서트 예약 만료 토큰 처리") {
        `when`("만료된 활성 토큰들을 조회할 때") {
            then("타임아웃된 토큰들이 반환된다") {
                // given
                val currentTime = System.currentTimeMillis()
                val ttlMillis = 600000L // 10분
                val expiredTime = currentTime - ttlMillis - 1000L // 10분 1초 전
                val validTime = currentTime - 300000L // 5분 전
                
                val activeTokens = setOf("expired-token", "valid-token", "no-timestamp-token")
                
                every { setOps.members("active_tokens") } returns activeTokens
                every { valueOps.get("active_timestamp:expired-token") } returns expiredTime.toString()
                every { valueOps.get("active_timestamp:valid-token") } returns validTime.toString()
                every { valueOps.get("active_timestamp:no-timestamp-token") } returns null

                // when
                val result = redisTokenStore.findExpiredActiveTokens()

                // then
                result shouldContain "expired-token" // 만료된 토큰
                result shouldContain "no-timestamp-token" // 타임스탬프 없는 토큰
                result.size shouldBe 2
            }
        }
        `when`("모든 활성 토큰이 아직 유효할 때") {
            then("빈 리스트가 반환된다") {
                // given
                val currentTime = System.currentTimeMillis()
                val validTime = currentTime - 300000L // 5분 전 (아직 유효)
                
                val activeTokens = setOf("valid-token1", "valid-token2")
                
                every { setOps.members("active_tokens") } returns activeTokens
                every { valueOps.get("active_timestamp:valid-token1") } returns validTime.toString()
                every { valueOps.get("active_timestamp:valid-token2") } returns validTime.toString()

                // when
                val result = redisTokenStore.findExpiredActiveTokens()

                // then
                result.shouldBeEmpty()
            }
        }
    }

    given("RedisTokenStore - 콘서트 예약 활성 사용자 관리") {
        `when`("콘서트 예약 중인 활성 사용자 수를 조회할 때") {
            then("현재 예약 중인 사용자 수가 반환된다") {
                // given
                val expectedCount = 100L // 최대 100명 동시 예약 가능
                every { setOps.size("active_tokens") } returns expectedCount

                // when
                val count = redisTokenStore.countActiveTokens()

                // then
                count shouldBe expectedCount
            }
        }
        `when`("콘서트 예약자가 없을 때") {
            then("0이 반환된다") {
                // given
                every { setOps.size("active_tokens") } returns 0L

                // when
                val count = redisTokenStore.countActiveTokens()

                // then
                count shouldBe 0L
            }
        }
    }
})
