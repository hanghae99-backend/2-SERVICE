package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.api.concert.dto.PopularConcertDto
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ConcertStatsService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val concertRepository: kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
) {
    
    companion object {
        private const val POPULAR_CONCERTS_KEY = "concerts:popular"
        private const val VIEW_COUNT_KEY = "concerts:views"
        private const val TRENDING_KEY = "concerts:trending"
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
