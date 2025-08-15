# Redis 캐싱 성능 분석 리포트

## 📋 개요

본 리포트는 콘서트 예약 시스템의 Redis 캐싱 구현 현황과 성능 개선 효과를 분석한 문서입니다. Spring Cache Abstraction과 Redis를 활용한 캐싱 전략의 효과성을 검증하고, 실제 성능 테스트 결과를 바탕으로 최적화 방안을 제시합니다.

## 🔍 캐시 구현 현황 분석

### 1. 적용된 캐싱 전략

#### 1.1 Spring Cache + Redis 조합
- **기술 스택**: Spring Cache Abstraction + Redis
- **캐시 어노테이션**: `@Cacheable` 활용
- **키 생성 전략**: SpEL 표현식 기반 동적 키 생성

#### 1.2 캐시 적용 서비스 분석

##### ConcertService 캐시 전략
```kotlin
// 1. 예약 가능한 콘서트 목록
@Cacheable(value = ["concerts:available"], key = "#startDate.toString() + ':' + #endDate.toString()")
fun getAvailableConcerts(startDate: LocalDate, endDate: LocalDate): List<ConcertScheduleWithInfoDto>

// 2. 콘서트 상세 정보 + 조회수 증가
@IncrementViewCount(concertIdParam = "concertId")
@Cacheable(value = ["concerts"], key = "#concertId")
fun getConcertById(concertId: Long): ConcertDto

// 3. 콘서트 스케줄 상세
@Cacheable(value = ["concerts:detail"], key = "#scheduleId")
fun getConcertDetailByScheduleId(scheduleId: Long): ConcertDetailDto

// 4. 콘서트별 스케줄 목록
@Cacheable(value = ["schedules"], key = "#concertId")
fun getSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto>
```

##### ConcertStatsService Redis 활용
```kotlin
// 1. 인기 콘서트 (Redis ZSet + Spring Cache)
@Cacheable(value = ["concerts:popular:main"], key = "#limit")
fun getPopularConcerts(limit: Int): List<PopularConcertDto>

// 2. 트렌딩 콘서트 (실시간 데이터)
@Cacheable(value = ["concerts:trending"], key = "#limit")
fun getTrendingConcerts(limit: Int): List<PopularConcertDto>
```

### 2. Redis 데이터 구조 활용

#### 2.1 ZSet (Sorted Set) 활용
- **용도**: 인기도/트렌딩 순위 관리
- **키 구조**:
  - `concerts:popular`: 전체 인기도 점수 기반 순위
  - `concerts:trending`: 실시간 트렌딩 점수 기반 순위

#### 2.2 Hash 활용
- **용도**: 조회수 카운터
- **키 구조**: `concerts:views` - 콘서트별 조회수 저장

#### 2.3 String 활용 (Spring Cache)
- **용도**: 메서드 실행 결과 캐싱
- **키 구조**: 
  - `concerts:available:{startDate}:{endDate}`
  - `concerts:{concertId}`
  - `concerts:detail:{scheduleId}`
  - `schedules:{concertId}`

## 📊 실제 성능 테스트 결과

### 1. 성능 테스트 결과 분석

#### 1.1 전체 성능 지표
| 메트릭 | 캐시 활성화 | 캐시 비활성화 | 개선율 |
|--------|-------------|---------------|--------|
| **평균 응답시간** | 78.99ms | 91.53ms | **13.7%** |
| **95% 응답시간** | 274.00ms | 274.00ms | **0.0%** |
| **처리량 (TPS)** | 159.68 req/s | 183.77 req/s | **-13.1%** |
| **총 요청 수** | 28,742건 | 33,078건 | - |

### 2. 캐시 효율성 분석

#### 2.1 성능 지표 해석
- **캐시 적중률 추정**: 13.7% (매우 낮음)
- **속도 향상 배수**: 1.2x (제한적 개선)
- **처리량**: 오히려 13.1% 감소 (캐시 오버헤드 발생)

#### 2.2 캐시 효과가 제한적인 원인 분석
1. **대기열 시스템의 영향**: 토큰 활성화 수 제한으로 동시 접근 사용자 수 제한
2. **캐시 키 분산**: 테스트에서 매번 다른 파라미터 사용으로 캐시 미스 빈발
3. **Redis 오버헤드**: 네트워크 통신 비용이 DB 조회 비용과 유사한 수준

## ⚠️ 아쉬웠던 점과 한계사항

### 1. 시스템 아키텍처의 한계

#### 1.1 대기열 시스템으로 인한 트래픽 제한
- **문제점**: 토큰 활성화 수가 제한적이어서 많은 사용자가 동시 접근하지 못함
- **영향**: 캐시 효과를 제대로 검증할 수 있는 충분한 트래픽 생성 어려움
- **결과**: 실제 운영 환경에서의 캐시 효과와 다른 결과 가능성

#### 1.2 테스트 환경의 제약
- **데이터 규모**: 제한적인 테스트 데이터로 인한 캐시 효과 미미
- **동시성**: 실제 피크 트래픽 대비 낮은 동시 사용자 수
- **지속성**: 짧은 테스트 시간으로 캐시 워밍업 효과 제한적


## 🔧 최적화 개선 방안

### 1. 즉시 적용 가능한 개선사항

#### 1.1 TTL(Time To Live) 설정
```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    
    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(60))  // 기본 1시간
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer()))
            )
            .withInitialCacheConfigurations(
                mapOf(
                    "concerts" to RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(6)),      // 콘서트 정보: 6시간
                    "concerts:available" to RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(30)),   // 예약가능 콘서트: 30분
                    "concerts:popular:main" to RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(15)),   // 인기 콘서트: 15분
                    "concerts:trending" to RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))     // 트렌딩: 5분
                )
            )
            .build()
        return cacheManager
    }
}
```

#### 1.2 캐시 무효화 전략 구현
```kotlin
@Service
@Transactional
class ConcertManagementService(
    private val cacheManager: CacheManager
) {
    
    @CacheEvict(value = ["concerts"], key = "#concertId")
    fun updateConcert(concertId: Long, updateDto: ConcertUpdateDto) {
        // 콘서트 정보 수정 시 해당 캐시 무효화
        concertRepository.save(updateDto.toEntity())
        
        // 관련 캐시도 함께 무효화
        evictRelatedCaches(concertId)
    }
    
    @CacheEvict(value = ["concerts:available"], allEntries = true)
    fun addNewConcertSchedule(scheduleDto: ConcertScheduleDto) {
        // 새 스케줄 추가 시 예약가능 콘서트 캐시 전체 무효화
        scheduleRepository.save(scheduleDto.toEntity())
    }
    
    private fun evictRelatedCaches(concertId: Long) {
        cacheManager.getCache("schedules")?.evict(concertId)
        cacheManager.getCache("concerts:popular:main")?.clear()
        cacheManager.getCache("concerts:trending")?.clear()
    }
}
```

### 2. 캐시 워밍업 전략

#### 2.1 시스템 시작 시 캐시 워밍업
```kotlin
@Component
class CacheWarmupService(
    private val concertService: ConcertService,
    private val concertStatsService: ConcertStatsService
) {
    
    @EventListener(ApplicationReadyEvent::class)
    @Async
    fun warmupCache() {
        try {
            // 인기 콘서트 미리 로딩
            concertStatsService.getPopularConcerts(10)
            concertStatsService.getTrendingConcerts(5)
            
            // 기본 날짜 범위 콘서트 미리 로딩
            val today = LocalDate.now()
            concertService.getAvailableConcerts(today, today.plusMonths(3))
            
            // TOP 10 콘서트 상세 정보 미리 로딩
            val popularConcerts = concertStatsService.getPopularConcerts(10)
            popularConcerts.forEach { concert ->
                concertService.getConcertById(concert.concertId)
            }
            
            logger.info("캐시 워밍업 완료")
        } catch (e: Exception) {
            logger.error("캐시 워밍업 실패", e)
        }
    }
}
```

#### 2.2 스케줄 기반 캐시 갱신
```kotlin
@Component
class CacheScheduler {
    
    @Scheduled(cron = "0 */10 * * * *") // 10분마다
    fun refreshPopularConcerts() {
        cacheManager.getCache("concerts:popular:main")?.clear()
        concertStatsService.getPopularConcerts(10) // 캐시 재생성
    }
    
    @Scheduled(cron = "0 */5 * * * *") // 5분마다
    fun refreshTrendingConcerts() {
        cacheManager.getCache("concerts:trending")?.clear()
        concertStatsService.getTrendingConcerts(5) // 캐시 재생성
    }
}
```
