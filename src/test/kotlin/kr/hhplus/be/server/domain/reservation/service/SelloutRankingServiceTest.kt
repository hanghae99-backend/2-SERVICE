package kr.hhplus.be.server.domain.reservation.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.time.LocalDateTime
import java.time.LocalDate

class SelloutRankingServiceTest : BehaviorSpec({

    val redisTemplate = mockk<StringRedisTemplate>()
    val reservationRepository = mockk<ReservationRepository>()
    val concertRepository = mockk<ConcertRepository>()
    val concertScheduleRepository = mockk<ConcertScheduleRepository>()
    val zSetOperations = mockk<ZSetOperations<String, String>>()
    
    val selloutRankingService = SelloutRankingService(
        redisTemplate = redisTemplate,
        reservationRepository = reservationRepository,
        concertRepository = concertRepository,
        concertScheduleRepository = concertScheduleRepository
    )
    
    beforeEach {
        clearAllMocks()
        every { redisTemplate.opsForZSet() } returns zSetOperations
    }

    given("매진 랭킹 조회") {
        `when`("Redis에 랭킹 데이터가 있을 때") {
            then("캐시된 랭킹을 반환한다") {
                // given
                val limit = 5
                val mockConcertIds = setOf("1", "2", "3")
                val mockConcerts = listOf(
                    Concert(concertId = 1L, title = "Test Concert 1", artist = "Test Artist 1", description = "Test Description 1", venue = "Test Venue 1"),
                    Concert(concertId = 2L, title = "Test Concert 2", artist = "Test Artist 2", description = "Test Description 2", venue = "Test Venue 2"),
                    Concert(concertId = 3L, title = "Test Concert 3", artist = "Test Artist 3", description = "Test Description 3", venue = "Test Venue 3")
                )
                
                every { zSetOperations.reverseRange("concerts:sellout_ranking", 0, limit - 1L) } returns mockConcertIds
                every { concertRepository.findAll() } returns mockConcerts
                every { zSetOperations.score("concerts:sellout_ranking", "1") } returns 10.0
                every { zSetOperations.score("concerts:sellout_ranking", "2") } returns 8.0
                every { zSetOperations.score("concerts:sellout_ranking", "3") } returns 5.0
                every { concertScheduleRepository.findByConcertIdIn(listOf(1L, 2L, 3L)) } returns emptyList()
                
                // when
                val result = selloutRankingService.getSelloutRanking(limit)
                
                // then
                result.size shouldBe 3
                result[0].concertId shouldBe 1L
                result[0].reservationsInLastHour shouldBe 10L
                result[0].ranking shouldBe 1
                
                verify { zSetOperations.reverseRange("concerts:sellout_ranking", 0, limit - 1L) }
            }
        }
        
        `when`("Redis에 데이터가 없을 때") {
            then("Fallback 랭킹을 반환한다") {
                // given
                val limit = 5
                val mockConcert = Concert(
                    concertId = 1L,
                    title = "Fallback Concert",
                    artist = "Fallback Artist",
                    description = "Fallback Description",
                    venue = "Fallback Venue"
                )
                
                every { zSetOperations.reverseRange("concerts:sellout_ranking", 0, limit - 1L) } returns emptySet()
                every { concertRepository.findByIsActiveTrue() } returns listOf(mockConcert)
                every { concertScheduleRepository.findByConcertId(1L) } returns emptyList()
                
                // when
                val result = selloutRankingService.getSelloutRanking(limit)
                
                // then
                result.size shouldBe 1
                result[0].concertId shouldBe 1L
                result[0].reservationsInLastHour shouldBe 0L
                result[0].ranking shouldBe 1
            }
        }
    }

    given("매진 랭킹 재구축") {
        `when`("DB에서 예약 수를 조회하여 랭킹을 재구축할 때") {
            then("Redis ZSet을 업데이트한다") {
                // given
                val mockReservationCounts = mapOf(
                    1L to 15L,
                    2L to 10L,
                    3L to 5L
                )
                
                every { reservationRepository.countReservationsByHour(any()) } returns mockReservationCounts
                every { redisTemplate.rename(any(), "concerts:sellout_ranking") } returns Unit
                every { redisTemplate.opsForValue().set("concerts:ranking:version", any<String>()) } returns Unit
                every { zSetOperations.add(any<String>(), "1", 15.0) } returns true
                every { zSetOperations.add(any<String>(), "2", 10.0) } returns true
                every { zSetOperations.add(any<String>(), "3", 5.0) } returns true
                
                // when
                selloutRankingService.rebuildSelloutRanking()
                
                // then
                verify { redisTemplate.rename(any(), "concerts:sellout_ranking") }
                verify { redisTemplate.opsForValue().set("concerts:ranking:version", any<String>()) }
                verify { zSetOperations.add(any<String>(), "1", 15.0) }
                verify { zSetOperations.add(any<String>(), "2", 10.0) }
                verify { zSetOperations.add(any<String>(), "3", 5.0) }
            }
        }
        
        `when`("DB에 데이터가 없을 때") {
            then("기존 랭킹을 초기화한다") {
                // given
                every { reservationRepository.countReservationsByHour(any()) } returns emptyMap()
                every { redisTemplate.delete(any<String>()) } returns true
                
                // when
                selloutRankingService.rebuildSelloutRanking()
                
                // then
                verify { redisTemplate.delete(any<String>()) }
                verify(exactly = 0) { zSetOperations.add(any(), any(), any<Double>()) }
            }
        }
    }

    given("예약 수 증가") {
        `when`("콘서트의 예약이 생성될 때") {
            then("Redis ZSet 스코어를 1 증가시킨다") {
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

    given("예약 수 감소") {
        `when`("콘서트의 예약이 취소/만료될 때") {
            then("Redis ZSet 스코어를 1 감소시킨다") {
                // given
                val concertId = 1L
                every { zSetOperations.incrementScore("concerts:sellout_ranking", "1", -1.0) } returns 9.0
                
                // when
                selloutRankingService.decrementReservationCount(concertId)
                
                // then
                verify { zSetOperations.incrementScore("concerts:sellout_ranking", "1", -1.0) }
            }
        }
    }

    given("랭킹 정리") {
        `when`("오래된 랭킹 데이터를 정리할 때") {
            then("비활성 콘서트들을 제거한다") {
                // given
                val activeConcerts = listOf(
                    Concert(concertId = 1L, title = "Active Concert 1", artist = "Artist 1", description = "Desc 1", venue = "Venue 1"),
                    Concert(concertId = 2L, title = "Active Concert 2", artist = "Artist 2", description = "Desc 2", venue = "Venue 2")
                )
                val currentRankingIds = setOf("1", "2", "3", "4") // 3, 4는 비활성 콘서트
                
                every { concertRepository.findByIsActiveTrue() } returns activeConcerts
                every { zSetOperations.range("concerts:sellout_ranking", 0, -1) } returns currentRankingIds
                every { zSetOperations.remove("concerts:sellout_ranking", "3") } returns 1L
                every { zSetOperations.remove("concerts:sellout_ranking", "4") } returns 1L
                
                // when
                selloutRankingService.cleanupSelloutRanking()
                
                // then
                verify { concertRepository.findByIsActiveTrue() }
                verify { zSetOperations.range("concerts:sellout_ranking", 0, -1) }
                verify { zSetOperations.remove("concerts:sellout_ranking", "3") }
                verify { zSetOperations.remove("concerts:sellout_ranking", "4") }
            }
        }
        
        `when`("정리할 데이터가 없을 때") {
            then("아무 작업도 하지 않는다") {
                // given
                val activeConcerts = listOf(
                    Concert(concertId = 1L, title = "Active Concert 1", artist = "Artist 1", description = "Desc 1", venue = "Venue 1")
                )
                val currentRankingIds = setOf("1") // 모든 ID가 활성 콘서트
                
                every { concertRepository.findByIsActiveTrue() } returns activeConcerts
                every { zSetOperations.range("concerts:sellout_ranking", 0, -1) } returns currentRankingIds
                
                // when
                selloutRankingService.cleanupSelloutRanking()
                
                // then
                verify { concertRepository.findByIsActiveTrue() }
                verify { zSetOperations.range("concerts:sellout_ranking", 0, -1) }
                verify(exactly = 0) { zSetOperations.remove(any(), any()) }
            }
        }
    }
})