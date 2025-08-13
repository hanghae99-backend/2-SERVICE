package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.api.concert.dto.*
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.global.extension.orElseThrow
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate


@Service
@Transactional(readOnly = true)
class ConcertService(
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatRepository: SeatRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) {
    
    companion object {
        private const val POPULAR_CONCERTS_KEY = "concerts:popular"
        private const val VIEW_COUNT_KEY = "concerts:views"
        private const val TRENDING_KEY = "concerts:trending"
    }
    
    @Cacheable(value = ["concerts:available"], key = "#startDate.toString() + ':' + #endDate.toString()")
    fun getAvailableConcerts(
        startDate: LocalDate = LocalDate.now(), 
        endDate: LocalDate = LocalDate.now().plusMonths(3)
    ): List<ConcertScheduleWithInfoDto> {
        val schedules = concertScheduleRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
            startDate, endDate, 0
        )
        
        if (schedules.isEmpty()) return emptyList()
        
        // 콘서트 정보를 한 번에 조회
        val concertIds = schedules.map { it.concertId }.distinct()
        val concerts = concertIds.mapNotNull { concertRepository.findById(it) }
        val concertMap = concerts.associateBy { it.concertId }
        
        return schedules.mapNotNull { schedule ->
            val concert = concertMap[schedule.concertId]
            if (concert != null) {
                ConcertScheduleWithInfoDto.from(concert, schedule)
            } else null
        }
    }
    
    @Cacheable(value = ["concerts"], key = "#concertId")
    fun getConcertById(concertId: Long): ConcertDto {
        val concert = concertRepository.findById(concertId)
            .orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId") }
        
        incrementViewCount(concertId)
        
        return ConcertDto.from(concert)
    }
    
    @Cacheable(value = ["concerts:detail"], key = "#scheduleId")
    fun getConcertDetailByScheduleId(scheduleId: Long): ConcertDetailDto {
        val schedule = concertScheduleRepository.findById(scheduleId)
            .orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        
        val concert = concertRepository.findById(schedule.concertId)
            .orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: ${schedule.concertId}") }
        
        val seats = seatRepository.findByScheduleId(scheduleId)
        
        return ConcertDetailDto.from(concert, schedule, seats)
    }
    
    @Cacheable(value = ["schedules"], key = "#concertId")
    fun getSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto> {
        val concert = concertRepository.findById(concertId).orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId") }
        
        val schedules = concertScheduleRepository.findByConcertId(concertId)
        
        return schedules.map { schedule ->
            ConcertWithScheduleDto.from(concert, schedule)
        }
    }
    
    @Cacheable(value = ["concerts:popular:main"], key = "#limit")
    fun getPopularConcerts(limit: Int = 10): List<PopularConcertDto> {
        val popularConcertIds = redisTemplate.opsForZSet()
            .reverseRange(POPULAR_CONCERTS_KEY, 0, limit.toLong() - 1)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
        
        if (popularConcertIds.isEmpty()) {
            return concertRepository.findByIsActiveTrue().take(limit).map { concert ->
                PopularConcertDto(
                    concertId = concert.concertId,
                    title = concert.title,
                    artist = concert.artist,
                    imageUrl = null,
                    popularityScore = 0.0,
                    viewCount = 0,
                    isHot = false
                )
            }
        }
        
        return buildPopularConcertDtos(popularConcertIds)
    }
    
    @Cacheable(value = ["concerts:trending"], key = "#limit")
    fun getTrendingConcerts(limit: Int = 5): List<PopularConcertDto> {
        val trendingIds = redisTemplate.opsForZSet()
            .reverseRange(TRENDING_KEY, 0, limit.toLong() - 1)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
        
        if (trendingIds.isEmpty()) {
            return getPopularConcerts(limit)
        }
        
        return buildPopularConcertDtos(trendingIds)
    }
    
    @Async
    fun incrementViewCount(concertId: Long) {
        redisTemplate.opsForHash<String, String>()
            .increment(VIEW_COUNT_KEY, concertId.toString(), 1)
        updatePopularityScore(concertId)
        updateTrendingScore(concertId, 1.0)
    }
    
    private fun buildPopularConcertDtos(concertIds: List<Long>): List<PopularConcertDto> {
        if (concertIds.isEmpty()) return emptyList()
        
        val concerts = concertRepository.findAll().filter { it.concertId in concertIds }
        val concertMap = concerts.associateBy { it.concertId }
        
        val viewCounts = redisTemplate.opsForHash<String, String>()
            .multiGet(VIEW_COUNT_KEY, concertIds.map { it.toString() })
        
        return concertIds.mapIndexed { index, concertId ->
            val concert = concertMap[concertId]
            val viewCount = viewCounts.getOrNull(index)?.toLongOrNull() ?: 0
            
            if (concert != null) {
                PopularConcertDto(
                    concertId = concert.concertId,
                    title = concert.title,
                    artist = concert.artist,
                    imageUrl = null,
                    popularityScore = viewCount.toDouble(),
                    viewCount = viewCount,
                    isHot = viewCount >= 100
                )
            } else null
        }.filterNotNull()
    }
    
    private fun updatePopularityScore(concertId: Long) {
        val viewCount = redisTemplate.opsForHash<String, String>()
            .get(VIEW_COUNT_KEY, concertId.toString())?.toDoubleOrNull() ?: 0.0
        redisTemplate.opsForZSet().add(POPULAR_CONCERTS_KEY, concertId, viewCount)
    }
    
    private fun updateTrendingScore(concertId: Long, weight: Double) {
        val currentTime = System.currentTimeMillis()
        val score = currentTime * weight
        redisTemplate.opsForZSet().add(TRENDING_KEY, concertId, score)
    }
    
    @Scheduled(fixedRate = 3600000)
    fun cleanupPopularityData() {
        val oneHourAgo = System.currentTimeMillis() - 3600000
        redisTemplate.opsForZSet().removeRangeByScore(TRENDING_KEY, 0.0, oneHourAgo.toDouble())
        
        val activeConcertIds = concertRepository.findByIsActiveTrue().map { it.concertId }.toSet()
        val currentPopularIds = redisTemplate.opsForZSet()
            .range(POPULAR_CONCERTS_KEY, 0, -1)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
        
        val inactiveIds = currentPopularIds.filter { it !in activeConcertIds }
        inactiveIds.forEach { concertId ->
            redisTemplate.opsForZSet().remove(POPULAR_CONCERTS_KEY, concertId)
            redisTemplate.opsForZSet().remove(TRENDING_KEY, concertId)
            redisTemplate.opsForHash<String, String>().delete(VIEW_COUNT_KEY, concertId.toString())
        }
    }
}
