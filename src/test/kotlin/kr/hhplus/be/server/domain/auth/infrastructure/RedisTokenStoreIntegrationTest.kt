package kr.hhplus.be.server.domain.auth.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.Spec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer

class RedisTokenStoreIntegrationTest : DescribeSpec() {
    
    private val redisContainer = GenericContainer<Nothing>("redis:7-alpine").apply {
        withExposedPorts(6379)
    }
    
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var objectMapper: ObjectMapper
    private lateinit var redisTokenStore: RedisTokenStore
    
    override suspend fun beforeSpec(spec: Spec) {
        // 컨테이너 시작
        redisContainer.start()
        
        // Redis 연결 설정
        val connectionFactory = LettuceConnectionFactory(
            redisContainer.host,
            redisContainer.getMappedPort(6379)
        ).apply {
            afterPropertiesSet()
        }
        
        redisTemplate = StringRedisTemplate().apply {
            setConnectionFactory(connectionFactory)
            afterPropertiesSet()
        }
        
        objectMapper = jacksonObjectMapper().apply {
            registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        
        redisTokenStore = RedisTokenStore(redisTemplate, objectMapper)
    }
    
    override suspend fun afterSpec(spec: Spec) {
        // 컨테이너 정리
        redisContainer.stop()
    }
    
    init {
        describe("RedisTokenStore 통합 테스트") {
            
            beforeEach {
                // 테스트 전 Redis 초기화
                redisTokenStore.flushAll()
            }
            
            context("실제 Redis와 연동하여 토큰 CRUD 테스트") {
                it("토큰 저장, 조회, 삭제가 정상적으로 동작해야 한다") {
                    // given
                    val waitingToken = WaitingToken(
                        token = "integration-test-token",
                        userId = 12345L
                    )
                    
                    // when & then - 저장
                    redisTokenStore.save(waitingToken)
                    
                    // when & then - 조회
                    val foundToken = redisTokenStore.findByToken(waitingToken.token)
                    foundToken shouldNotBe null
                    foundToken!!.token shouldBe waitingToken.token
                    foundToken.userId shouldBe waitingToken.userId
                    
                    // when & then - 검증
                    redisTokenStore.validate(waitingToken.token) shouldBe true
                    
                    // when & then - 삭제
                    redisTokenStore.delete(waitingToken.token)
                    redisTokenStore.findByToken(waitingToken.token) shouldBe null
                    redisTokenStore.validate(waitingToken.token) shouldBe false
                }
            }
            
            context("실제 Redis와 연동하여 대기열 관리 테스트") {
                it("대기열 추가, 순서 조회, 토큰 가져오기가 정상적으로 동작해야 한다") {
                    // given
                    val tokens = listOf("token1", "token2", "token3", "token4", "token5")
                    
                    // when - 대기열에 추가
                    tokens.forEach { redisTokenStore.addToWaitingQueue(it) }
                    
                    // then - 대기열 크기 확인
                    redisTokenStore.getQueueSize() shouldBe 5L
                    
                    // then - 대기 순서 확인
                    redisTokenStore.getQueuePosition("token1") shouldBe 0
                    redisTokenStore.getQueuePosition("token3") shouldBe 2
                    redisTokenStore.getQueuePosition("token5") shouldBe 4
                    
                    // when - 대기열에서 토큰 가져오기
                    val nextTokens = redisTokenStore.getNextTokensFromQueue(3)
                    
                    // then - FIFO 순서 확인
                    nextTokens shouldHaveSize 3
                    nextTokens shouldContain "token1"
                    nextTokens shouldContain "token2"
                    nextTokens shouldContain "token3"
                    
                    // then - 남은 대기열 확인
                    redisTokenStore.getQueueSize() shouldBe 2L
                    redisTokenStore.getQueuePosition("token4") shouldBe 0
                    redisTokenStore.getQueuePosition("token5") shouldBe 1
                }
            }
            
            context("실제 Redis와 연동하여 토큰 상태 관리 테스트") {
                it("토큰 활성화와 만료가 정상적으로 동작해야 한다") {
                    // given
                    val token = "status-test-token"
                    redisTokenStore.addToWaitingQueue(token)
                    
                    // when & then - 대기 상태 확인
                    redisTokenStore.getTokenStatus(token) shouldBe TokenStatus.WAITING
                    
                    // when - 토큰 활성화
                    redisTokenStore.activateToken(token)
                    
                    // then - 활성 상태 확인
                    redisTokenStore.getTokenStatus(token) shouldBe TokenStatus.ACTIVE
                    redisTokenStore.countActiveTokens() shouldBe 1L
                    
                    // when - 토큰 만료
                    redisTokenStore.expireToken(token)
                    
                    // then - 만료 상태 확인
                    redisTokenStore.getTokenStatus(token) shouldBe TokenStatus.EXPIRED
                    redisTokenStore.countActiveTokens() shouldBe 0L
                }
            }
            
            context("실제 Redis와 연동하여 만료된 토큰 찾기 테스트") {
                it("만료된 활성 토큰을 올바르게 찾아야 한다") {
                    // given - 토큰들 준비
                    val tokens = listOf("expired1", "expired2", "valid1")
                    
                    // when - 토큰들 활성화 (타임스탬프 조작을 위해 직접 Redis 조작)
                    tokens.forEach { token ->
                        redisTemplate.opsForSet().add("active_tokens", token)
                    }
                    
                    // 만료된 토큰들의 타임스탬프 설정 (15분 전)
                    val expiredTimestamp = (System.currentTimeMillis() - 15 * 60 * 1000).toString()
                    redisTemplate.opsForValue().set("active_timestamp:expired1", expiredTimestamp)
                    redisTemplate.opsForValue().set("active_timestamp:expired2", expiredTimestamp)
                    
                    // 유효한 토큰의 타임스탬프 설정 (5분 전)
                    val validTimestamp = (System.currentTimeMillis() - 5 * 60 * 1000).toString()
                    redisTemplate.opsForValue().set("active_timestamp:valid1", validTimestamp)
                    
                    // when - 만료된 토큰 찾기
                    val expiredTokens = redisTokenStore.findExpiredActiveTokens()
                    
                    // then
                    expiredTokens shouldHaveSize 2
                    expiredTokens shouldContain "expired1"
                    expiredTokens shouldContain "expired2"
                }
            }
            
            context("대량 데이터 처리 성능 테스트") {
                it("1000개 토큰을 빠르게 처리할 수 있어야 한다") {
                    // given
                    val tokenCount = 1000
                    val tokens = (1..tokenCount).map { "perf-token-$it" }
                    
                    // when - 대량 토큰 대기열 추가
                    val startTime = System.currentTimeMillis()
                    tokens.forEach { redisTokenStore.addToWaitingQueue(it) }
                    val addTime = System.currentTimeMillis() - startTime
                    
                    // then - 성능 확인 (5초 이내로 늘림)
                    addTime shouldBeLessThan 5000L
                    redisTokenStore.getQueueSize() shouldBe tokenCount.toLong()
                    
                    // when - 대량 토큰 가져오기
                    val fetchStartTime = System.currentTimeMillis()
                    val fetchedTokens = redisTokenStore.getNextTokensFromQueue(tokenCount)
                    val fetchTime = System.currentTimeMillis() - fetchStartTime
                    
                    // then - 성능 확인 (5초 이내로 늘림)
                    fetchTime shouldBeLessThan 5000L
                    fetchedTokens shouldHaveSize tokenCount
                }
            }
        }
    }
}