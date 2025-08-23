package kr.hhplus.be.server.domain.reservation.service

import kr.hhplus.be.server.api.concert.dto.SelloutRankingDto
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class SelloutRankingService(
    private val redisTemplate: StringRedisTemplate,
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val reservationRepository: ReservationRepository
) {
    
    companion object {
        private const val SELLOUT_RANKING_KEY = "concerts:sellout_ranking"
        private const val RANKING_VERSION_KEY = "concerts:ranking:version"
    }
    
    @Cacheable(value = ["concerts:sellout:main"], key = "#limit")
    fun getSelloutRanking(limit: Int = 10): List<SelloutRankingDto> {
        val rankingIds = redisTemplate.opsForZSet()
            .reverseRange(SELLOUT_RANKING_KEY, 0, limit.toLong() - 1)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
        
        if (rankingIds.isEmpty()) {
            return getFallbackRanking(limit)
        }
        
        return buildSelloutRankingDtos(rankingIds)
    }
    
    @Async
    fun incrementReservationCount(concertId: Long) {
        redisTemplate.opsForZSet()
            .incrementScore(SELLOUT_RANKING_KEY, concertId.toString(), 1.0)
    }
    
    @Async
    fun decrementReservationCount(concertId: Long) {
        redisTemplate.opsForZSet()
            .incrementScore(SELLOUT_RANKING_KEY, concertId.toString(), -1.0)
    }
    
    fun rebuildSelloutRanking() {
        val newVersion = System.currentTimeMillis()
        val tempKey = "${SELLOUT_RANKING_KEY}:${newVersion}"
        
        // DB에서 최근 1시간 예약 수 조회
        val oneHourAgo = LocalDateTime.now().minusHours(1)
        val reservationCounts = reservationRepository.countReservationsByHour(oneHourAgo)
        
        // 임시 키에 데이터 저장
        reservationCounts.forEach { (concertId, count) ->
            redisTemplate.opsForZSet().add(tempKey, concertId.toString(), count.toDouble())
        }
        
        // 원자적 교체
        if (reservationCounts.isNotEmpty()) {
            redisTemplate.rename(tempKey, SELLOUT_RANKING_KEY)
            redisTemplate.opsForValue().set(RANKING_VERSION_KEY, newVersion.toString())
        } else {
            redisTemplate.delete(tempKey)
        }
    }
    
    private fun buildSelloutRankingDtos(concertIds: List<Long>): List<SelloutRankingDto> {
        if (concertIds.isEmpty()) return emptyList()
        
        val concerts = concertRepository.findAll().filter { it.concertId in concertIds }
        val concertMap = concerts.associateBy { it.concertId }
        
        // Redis에서 예약 수 조회
        val reservationCounts = concertIds.associate { concertId ->
            concertId.toString() to (redisTemplate.opsForZSet().score(SELLOUT_RANKING_KEY, concertId.toString()) ?: 0.0)
        }
        
        // 좌석 정보 조회
        val scheduleInfoMap = concertScheduleRepository.findByConcertIdIn(concertIds)
            .groupBy { it.concertId }
            .mapValues { (_, schedules) ->
                val totalSeats = schedules.sumOf { it.totalSeats }
                val availableSeats = schedules.sumOf { it.availableSeats }
                val nextShowDate = schedules.minOfOrNull { it.concertDate }?.atStartOfDay()
                Triple(totalSeats, availableSeats, nextShowDate)
            }
        
        return concertIds.mapIndexed { index, concertId ->
            val concert = concertMap[concertId] ?: return@mapIndexed null
            val reservationCount = reservationCounts[concertId.toString()]?.toLong() ?: 0L
            val scheduleInfo = scheduleInfoMap[concertId]
            val (totalSeats, availableSeats, nextShowDate) = scheduleInfo ?: Triple(0, 0, null)
            
            SelloutRankingDto.from(
                concert = concert,
                reservationCount = reservationCount,
                totalSeats = totalSeats,
                availableSeats = availableSeats,
                ranking = index + 1,
                nextShowDate = nextShowDate
            )
        }.filterNotNull()
    }
    
    private fun getFallbackRanking(limit: Int): List<SelloutRankingDto> {
        val concerts = concertRepository.findByIsActiveTrue().take(limit)
        
        return concerts.mapIndexed { index, concert ->
            val scheduleInfo = concertScheduleRepository.findByConcertId(concert.concertId).firstOrNull()
            val totalSeats = scheduleInfo?.totalSeats ?: 0
            val availableSeats = scheduleInfo?.availableSeats ?: 0
            val nextShowDate = scheduleInfo?.concertDate?.atStartOfDay()
            
            SelloutRankingDto.from(
                concert = concert,
                reservationCount = 0,
                totalSeats = totalSeats,
                availableSeats = availableSeats,
                ranking = index + 1,
                nextShowDate = nextShowDate
            )
        }
    }
    
    fun cleanupSelloutRanking() {
        val activeConcertIds = concertRepository.findByIsActiveTrue().map { it.concertId }.toSet()
        val currentRankingIds = redisTemplate.opsForZSet()
            .range(SELLOUT_RANKING_KEY, 0, -1)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
        
        val inactiveIds = currentRankingIds.filter { it !in activeConcertIds }
        inactiveIds.forEach { concertId ->
            redisTemplate.opsForZSet().remove(SELLOUT_RANKING_KEY, concertId.toString())
        }
    }
}