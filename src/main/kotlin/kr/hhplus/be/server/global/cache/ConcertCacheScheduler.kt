package kr.hhplus.be.server.global.cache

import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.api.concert.dto.ConcertScheduleWithInfoDto
import kr.hhplus.be.server.api.concert.dto.PopularConcertDto
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class ConcertCacheScheduler(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository
) {
    
    private val logger = LoggerFactory.getLogger(ConcertCacheScheduler::class.java)
    
    companion object {
        // 캐시 키 상수
        private const val AVAILABLE_CONCERTS_DEFAULT = "cache:concerts:available:default"
        private const val AVAILABLE_CONCERTS_WEEKLY = "cache:concerts:available:weekly"
        private const val AVAILABLE_CONCERTS_MONTHLY = "cache:concerts:available:monthly"
        private const val POPULAR_CONCERTS_TOP10 = "cache:concerts:popular:top10"
        private const val POPULAR_CONCERTS_TOP5 = "cache:concerts:popular:top5"
        private const val TRENDING_CONCERTS_TOP5 = "cache:concerts:trending:top5"
        private const val CONCERT_DETAILS_PREFIX = "cache:concerts:detail:"
        private const val CONCERT_SCHEDULES_PREFIX = "cache:concerts:schedules:"
        
        // Redis ZSet 키
        private const val POPULAR_CONCERTS_KEY = "concerts:popular"
        private const val VIEW_COUNT_KEY = "concerts:views"
        private const val TRENDING_KEY = "concerts:trending"
    }
    
    // 애플리케이션 시작 후 초기 캐시 로딩
    @EventListener(ApplicationReadyEvent::class)
    @Async
    fun initializeCache() {
        logger.info("🔥 시스템 시작 - 초기 캐시 데이터 로딩 시작...")
        
        try {
            Thread.sleep(3000) // 3초 대기 (다른 빈들의 초기화 완료 대기)
            
            refreshAvailableConcerts()
            refreshPopularConcerts()
            refreshConcertDetails()
            
            logger.info("✅ 초기 캐시 데이터 로딩 완료")
        } catch (e: Exception) {
            logger.error("❌ 초기 캐시 로딩 실패", e)
        }
    }
    
    // 매 10분마다 예약 가능한 콘서트 갱신
    @Scheduled(cron = "0 */10 * * * *")
    fun refreshAvailableConcerts() {
        logger.debug("📅 예약 가능한 콘서트 캐시 갱신 중...")
        
        try {
            val today = LocalDate.now()
            
            // 기본 범위 (3개월)
            val defaultConcerts = getAvailableConcertsFromDB(today, today.plusMonths(3))
            redisTemplate.opsForValue().set(AVAILABLE_CONCERTS_DEFAULT, defaultConcerts, 15, TimeUnit.MINUTES)
            
            // 주간 범위
            val weeklyConcerts = getAvailableConcertsFromDB(today, today.plusWeeks(1))
            redisTemplate.opsForValue().set(AVAILABLE_CONCERTS_WEEKLY, weeklyConcerts, 15, TimeUnit.MINUTES)
            
            // 월간 범위
            val monthlyConcerts = getAvailableConcertsFromDB(today, today.plusMonths(1))
            redisTemplate.opsForValue().set(AVAILABLE_CONCERTS_MONTHLY, monthlyConcerts, 15, TimeUnit.MINUTES)
            
            logger.debug("✅ 예약 가능한 콘서트 캐시 갱신 완료")
        } catch (e: Exception) {
            logger.error("❌ 예약 가능한 콘서트 캐시 갱신 실패", e)
        }
    }
    
    // 매 5분마다 인기/트렌딩 콘서트 갱신
    @Scheduled(cron = "0 */5 * * * *")
    fun refreshPopularConcerts() {
        logger.debug("🔥 인기 콘서트 캐시 갱신 중...")
        
        try {
            // Redis ZSet에서 인기 콘서트 조회
            val popularConcertIds = redisTemplate.opsForZSet()
                .reverseRange(POPULAR_CONCERTS_KEY, 0, 9) // TOP 10
                ?.mapNotNull { it.toString().toLongOrNull() }
                ?: emptyList()
            
            if (popularConcertIds.isNotEmpty()) {
                val top10 = buildPopularConcertDtos(popularConcertIds)
                val top5 = top10.take(5)
                
                redisTemplate.opsForValue().set(POPULAR_CONCERTS_TOP10, top10, 10, TimeUnit.MINUTES)
                redisTemplate.opsForValue().set(POPULAR_CONCERTS_TOP5, top5, 10, TimeUnit.MINUTES)
            } else {
                // 기본 데이터로 초기화
                val defaultConcerts = getDefaultPopularConcerts()
                redisTemplate.opsForValue().set(POPULAR_CONCERTS_TOP10, defaultConcerts.take(10), 10, TimeUnit.MINUTES)
                redisTemplate.opsForValue().set(POPULAR_CONCERTS_TOP5, defaultConcerts.take(5), 10, TimeUnit.MINUTES)
            }
            
            // 트렌딩 콘서트
            val trendingIds = redisTemplate.opsForZSet()
                .reverseRange(TRENDING_KEY, 0, 4) // TOP 5
                ?.mapNotNull { it.toString().toLongOrNull() }
                ?: emptyList()
            
            if (trendingIds.isNotEmpty()) {
                val trending = buildPopularConcertDtos(trendingIds)
                redisTemplate.opsForValue().set(TRENDING_CONCERTS_TOP5, trending, 5, TimeUnit.MINUTES)
            } else {
                // 기본 데이터로 초기화
                val defaultConcerts = getDefaultPopularConcerts()
                redisTemplate.opsForValue().set(TRENDING_CONCERTS_TOP5, defaultConcerts.take(5), 5, TimeUnit.MINUTES)
            }
            
            logger.debug("✅ 인기 콘서트 캐시 갱신 완료")
        } catch (e: Exception) {
            logger.error("❌ 인기 콘서트 캐시 갱신 실패", e)
        }
    }
    
    // 매 30분마다 인기 콘서트 상세 정보 갱신
    @Scheduled(cron = "0 */30 * * * *")
    fun refreshConcertDetails() {
        logger.debug("📄 콘서트 상세 정보 캐시 갱신 중...")
        
        try {
            // 인기 콘서트 TOP 10의 상세 정보를 미리 캐시
            val popularConcertIds = redisTemplate.opsForZSet()
                .reverseRange(POPULAR_CONCERTS_KEY, 0, 9)
                ?.mapNotNull { it.toString().toLongOrNull() }
                ?: listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L) // 기본값
            
            popularConcertIds.forEach { concertId ->
                try {
                    // 콘서트 상세 정보
                    val concert = concertRepository.findById(concertId)
                    if (concert != null) {
                        redisTemplate.opsForValue().set(
                            "$CONCERT_DETAILS_PREFIX$concertId", 
                            concert, 
                            1, TimeUnit.HOURS
                        )
                        
                        // 콘서트 스케줄 정보
                        val schedules = concertScheduleRepository.findByConcertId(concertId)
                        redisTemplate.opsForValue().set(
                            "$CONCERT_SCHEDULES_PREFIX$concertId", 
                            schedules, 
                            1, TimeUnit.HOURS
                        )
                    }
                } catch (e: Exception) {
                    logger.warn("콘서트 $concertId 상세 정보 캐시 실패: ${e.message}")
                }
            }
            
            logger.debug("✅ 콘서트 상세 정보 캐시 갱신 완료")
        } catch (e: Exception) {
            logger.error("❌ 콘서트 상세 정보 캐시 갱신 실패", e)
        }
    }
    
    // 매일 새벽 3시에 전체 캐시 리프레시
    @Scheduled(cron = "0 0 3 * * *")
    fun dailyCacheRefresh() {
        logger.info("🌙 일일 전체 캐시 리프레시 시작...")
        
        try {
            refreshAvailableConcerts()
            refreshPopularConcerts()
            refreshConcertDetails()
            
            logger.info("✅ 일일 전체 캐시 리프레시 완료")
        } catch (e: Exception) {
            logger.error("❌ 일일 캐시 리프레시 실패", e)
        }
    }
    
    // === 헬퍼 메서드들 ===
    
    private fun getAvailableConcertsFromDB(startDate: LocalDate, endDate: LocalDate): List<ConcertScheduleWithInfoDto> {
        val schedules = concertScheduleRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
            startDate, endDate, 0
        )
        
        if (schedules.isEmpty()) return emptyList()
        
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
    
    private fun getDefaultPopularConcerts(): List<PopularConcertDto> {
        return concertRepository.findByIsActiveTrue().take(10).map { concert ->
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
}