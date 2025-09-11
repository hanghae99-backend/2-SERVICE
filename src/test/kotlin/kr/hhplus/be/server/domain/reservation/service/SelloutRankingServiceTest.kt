package kr.hhplus.be.server.domain.reservation.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations

class SelloutRankingServiceTest : BehaviorSpec({

    val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
    val reservationRepository = mockk<ReservationRepository>(relaxed = true)
    val concertRepository = mockk<ConcertRepository>(relaxed = true)
    val concertScheduleRepository = mockk<ConcertScheduleRepository>(relaxed = true)
    val zSetOperations = mockk<ZSetOperations<String, Any>>(relaxed = true)
    
    val selloutRankingService = SelloutRankingService(
        redisTemplate = redisTemplate,
        reservationRepository = reservationRepository,
        concertRepository = concertRepository,
        concertScheduleRepository = concertScheduleRepository
    )
    
    beforeEach {
        every { redisTemplate.opsForZSet() } returns zSetOperations
    }

    given("매진 랭킹 조회") {
        `when`("랭킹을 조회할 때") {
            then("랭킹 리스트를 반환한다") {
                // given
                val limit = 5
                val mockConcertIds = setOf("1", "2")
                val mockConcerts = listOf(
                    Concert.create("Test Concert 1", "Test Artist 1").apply { concertId = 1L },
                    Concert.create("Test Concert 2", "Test Artist 2").apply { concertId = 2L }
                )
                
                every { zSetOperations.reverseRange("concerts:sellout_ranking", 0, limit - 1L) } returns mockConcertIds
                every { concertRepository.findAll() } returns mockConcerts
                every { zSetOperations.score("concerts:sellout_ranking", "1") } returns 10.0
                every { zSetOperations.score("concerts:sellout_ranking", "2") } returns 8.0
                every { concertScheduleRepository.findByConcertIdIn(listOf(1L, 2L)) } returns emptyList()
                
                // when
                val result = selloutRankingService.getSelloutRanking(limit)
                
                // then
                result.size shouldBe 2
                result[0].concertId shouldBe 1L
                result[0].reservationsInLastHour shouldBe 10L
                result[0].ranking shouldBe 1
            }
        }
    }

    given("예약 수 증가") {
        `when`("콘서트의 예약이 생성될 때") {
            then("Redis ZSet 스코어를 증가시킨다") {
                // given
                val concertId = 1L
                every { zSetOperations.incrementScore("concerts:sellout_ranking", "1", 1.0) } returns 11.0
                
                // when
                selloutRankingService.incrementReservationCount(concertId)
                
                // then
                verify { zSetOperations.incrementScore("concerts:sellout_ranking", "1", 1.0) }
            }
        }
    }
})
