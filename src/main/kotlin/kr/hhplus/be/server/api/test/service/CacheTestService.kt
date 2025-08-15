package kr.hhplus.be.server.api.test.service

import kr.hhplus.be.server.api.concert.dto.PopularConcertDto
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
@Transactional(readOnly = true)
class CacheTestService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val concertRepository: ConcertRepository,
    private val cacheManager: CacheManager
) {
    
    private val logger = LoggerFactory.getLogger(CacheTestService::class.java)
    
    companion object {
        private const val POPULAR_CONCERTS_KEY = "concerts:popular"
        private const val VIEW_COUNT_KEY = "concerts:views"
        
        // 스케줄러 캐시 키
        private const val SCHEDULER_CACHE_KEY_PREFIX = "test:scheduler:popular:"
    }
    
    // 1. 스케줄러 방식 (미리 캐시된 데이터 조회)
    fun getPopularConcertsScheduler(limit: Int): List<PopularConcertDto> {
        val cacheKey = "$SCHEDULER_CACHE_KEY_PREFIX$limit"
        
        return try {
            val cached = redisTemplate.opsForValue().get(cacheKey)
            if (cached is List<*>) {
                @Suppress("UNCHECKED_CAST")
                cached as List<PopularConcertDto>
            } else {
                // 캐시 미스 시 생성 후 저장
                val result = buildPopularConcertDtos(limit)
                redisTemplate.opsForValue().set(cacheKey, result, 10, TimeUnit.MINUTES)
                result
            }
        } catch (e: Exception) {
            logger.warn("스케줄러 캐시 조회 실패: ${e.message}")
            buildPopularConcertDtos(limit)
        }
    }
    
    // 2. @Cacheable 방식 (Spring Cache 활용)
    @Cacheable(value = ["test:cacheable:popular"], key = "#limit")
    fun getPopularConcertsCacheable(limit: Int): List<PopularConcertDto> {
        return buildPopularConcertDtos(limit)
    }
    
    // 3. 직접 조회 방식 (캐시 없음)
    fun getPopularConcertsDirect(limit: Int): List<PopularConcertDto> {
        return buildPopularConcertDtos(limit)
    }
    
    // 공통 데이터 구축 로직
    private fun buildPopularConcertDtos(limit: Int): List<PopularConcertDto> {
        // 1. Redis ZSet에서 인기 콘서트 ID 조회
        val popularConcertIds = redisTemplate.opsForZSet()
            .reverseRange(POPULAR_CONCERTS_KEY, 0, limit.toLong() - 1)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
        
        if (popularConcertIds.isEmpty()) {
            // 기본 데이터 반환
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
        
        // 2. 콘서트 정보 조회
        val concerts = concertRepository.findAll().filter { it.concertId in popularConcertIds }
        val concertMap = concerts.associateBy { it.concertId }
        
        // 3. 조회수 정보 조회
        val viewCounts = redisTemplate.opsForHash<String, String>()
            .multiGet(VIEW_COUNT_KEY, popularConcertIds.map { it.toString() })
        
        // 4. DTO 구성
        return popularConcertIds.mapIndexed { index, concertId ->
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
    
    // 캐시 워밍업 (스케줄러 캐시 미리 생성)
    fun warmupCaches(limit: Int) {
        logger.info("캐시 워밍업 시작...")
        
        try {
            // 스케줄러 캐시 워밍업
            val popularLimits = listOf(5, 10, 15, 20)
            popularLimits.forEach { l ->
                val result = buildPopularConcertDtos(l)
                val cacheKey = "$SCHEDULER_CACHE_KEY_PREFIX$l"
                redisTemplate.opsForValue().set(cacheKey, result, 10, TimeUnit.MINUTES)
                logger.debug("스케줄러 캐시 생성: $cacheKey (${result.size}건)")
            }
            
            // @Cacheable 캐시 워밍업
            popularLimits.forEach { l ->
                getPopularConcertsCacheable(l)
                logger.debug("@Cacheable 캐시 생성: limit=$l")
            }
            
            logger.info("캐시 워밍업 완료")
        } catch (e: Exception) {
            logger.error("캐시 워밍업 실패", e)
        }
    }
    
    // 모든 캐시 삭제
    fun clearAllCaches() {
        logger.info("모든 캐시 삭제 시작...")
        
        try {
            // 스케줄러 캐시 삭제
            val schedulerKeys = redisTemplate.keys("$SCHEDULER_CACHE_KEY_PREFIX*") ?: emptySet()
            if (schedulerKeys.isNotEmpty()) {
                redisTemplate.delete(schedulerKeys)
                logger.debug("스케줄러 캐시 삭제: ${schedulerKeys.size}개")
            }
            
            // @Cacheable 캐시 삭제
            cacheManager.getCache("test:cacheable:popular")?.clear()
            logger.debug("@Cacheable 캐시 삭제 완료")
            
            logger.info("모든 캐시 삭제 완료")
        } catch (e: Exception) {
            logger.error("캐시 삭제 실패", e)
        }
    }
    
    // 캐시 상태 확인
    fun getCacheStatus(): Map<String, Any> {
        val status = mutableMapOf<String, Any>()
        
        try {
            // 스케줄러 캐시 상태
            val schedulerKeys = redisTemplate.keys("$SCHEDULER_CACHE_KEY_PREFIX*") ?: emptySet()
            status["scheduler_cache_count"] = schedulerKeys.size
            status["scheduler_cache_keys"] = schedulerKeys.toList()
            
            // @Cacheable 캐시 상태
            val cacheableCache = cacheManager.getCache("test:cacheable:popular")
            status["cacheable_cache_exists"] = cacheableCache != null
            
            // Redis 연결 상태
            redisTemplate.connectionFactory?.connection?.ping()
            status["redis_connected"] = true
            
        } catch (e: Exception) {
            status["error"] = e.message.toString()
            status["redis_connected"] = false
        }
        
        return status
    }
}