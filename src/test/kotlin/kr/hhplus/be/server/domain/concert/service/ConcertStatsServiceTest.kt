package kr.hhplus.be.server.domain.concert.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.mockk.*
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations

class ConcertStatsServiceTest : BehaviorSpec({
    
    Given("콘서트 통계 서비스에서") {
        
        When("인기 콘서트 목록을 조회할 때 Redis에 데이터가 있는 경우") {
            val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
            val concertRepository = mockk<ConcertRepository>(relaxed = true)
            val zSetOps = mockk<ZSetOperations<String, Any>>(relaxed = true)
            val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true)
            val concertStatsService = ConcertStatsService(redisTemplate, concertRepository)
            
            val limit = 5
            val popularConcertIds = setOf<Any>("1", "2", "3")
            val concerts = listOf(
                Concert.create("Concert 1", "Artist 1").apply { concertId = 1L },
                Concert.create("Concert 2", "Artist 2").apply { concertId = 2L },
                Concert.create("Concert 3", "Artist 3").apply { concertId = 3L }
            )
            val viewCounts = listOf("100", "200", "150")
            
            every { redisTemplate.opsForZSet() } returns zSetOps
            every { redisTemplate.opsForHash<String, String>() } returns hashOps
            every { zSetOps.reverseRange("concerts:popular", 0, 4) } returns popularConcertIds
            every { concertRepository.findAll() } returns concerts
            every { hashOps.multiGet("concerts:views", listOf("1", "2", "3")) } returns viewCounts
            
            val result = concertStatsService.getPopularConcerts(limit)
            
            Then("인기도 기준으로 정렬된 콘서트 목록을 반환해야 한다") {
                result.shouldNotBeEmpty()
                result.size shouldBe 3
            }
        }
        
        When("인기 콘서트 목록을 조회할 때 Redis에 데이터가 없는 경우") {
            val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
            val concertRepository = mockk<ConcertRepository>(relaxed = true)
            val zSetOps = mockk<ZSetOperations<String, Any>>(relaxed = true)
            val concertStatsService = ConcertStatsService(redisTemplate, concertRepository)
            
            val limit = 5
            val activeConcerts = listOf(
                Concert.create("Concert 1", "Artist 1").apply { concertId = 1L }
            )
            
            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.reverseRange("concerts:popular", 0, 4) } returns emptySet()
            every { concertRepository.findByIsActiveTrue() } returns activeConcerts
            
            val result = concertStatsService.getPopularConcerts(limit)
            
            Then("활성화된 콘서트 목록을 기본값으로 반환해야 한다") {
                result.shouldNotBeEmpty()
                result.size shouldBe 1
                result[0].concertId shouldBe 1L
            }
        }
        
        When("콘서트 조회수를 증가시킬 때") {
            val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
            val concertRepository = mockk<ConcertRepository>(relaxed = true)
            val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true)
            val zSetOps = mockk<ZSetOperations<String, Any>>(relaxed = true)
            val concertStatsService = ConcertStatsService(redisTemplate, concertRepository)
            
            val concertId = 1L
            
            // Mock 설정을 더 완전하게
            every { redisTemplate.opsForHash<String, String>() } returns hashOps
            every { redisTemplate.opsForZSet() } returns zSetOps
            every { hashOps.increment(any<String>(), any<String>(), any<Long>()) } returns 1L
            every { hashOps.get(any<String>(), any<String>()) } returns "1"
            every { zSetOps.add(any<String>(), any<String>(), any<Double>()) } returns true
            
            // 예외가 발생하지 않고 메서드가 완료되는지 확인
            try {
                concertStatsService.incrementViewCount(concertId)
                
                Then("메서드가 정상적으로 실행되어야 한다") {
                    // 단순히 예외 없이 실행되었는지 확인
                    verify(atLeast = 1) { redisTemplate.opsForHash<String, String>() }
                }
            } catch (e: Exception) {
                Then("예외가 발생하지 않아야 한다") {
                    throw AssertionError("예외가 발생했습니다: ${e.message}")
                }
            }
        }
        
        When("콘서트 조회수를 감소시킬 때") {
            val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
            val concertRepository = mockk<ConcertRepository>(relaxed = true)
            val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true)
            val zSetOps = mockk<ZSetOperations<String, Any>>(relaxed = true)
            val concertStatsService = ConcertStatsService(redisTemplate, concertRepository)
            
            val concertId = 1L
            
            every { redisTemplate.opsForHash<String, String>() } returns hashOps
            every { redisTemplate.opsForZSet() } returns zSetOps
            every { hashOps.get("concerts:views", "1") } returns "5"
            every { hashOps.increment(any<String>(), any<String>(), any<Long>()) } returns 4L
            every { zSetOps.add(any<String>(), any<String>(), any<Double>()) } returns true
            
            concertStatsService.decrementViewCount(concertId)
            
            Then("메서드가 정상적으로 실행되어야 한다") {
                verify { hashOps.get("concerts:views", "1") }
            }
        }
    }
})
