# Redis 캐싱 전략 및 구현 보고서

---

## 📋 개요

콘서트 예약 시스템에서 성능 최적화를 위해 Redis 기반 캐싱을 구현하고 적용한 내용을 정리한 보고서입니다.

---

## 🎯 캐싱 도입 배경

### 성능 최적화 필요성

**조회 성능 병목점**
- 콘서트 정보, 좌석 정보 등은 자주 조회되지만 변경 빈도가 낮음
- DB 부하 증가로 인한 응답 속도 저하
- 대용량 트래픽 상황에서 Read 성능이 시스템 전체 성능을 좌우

**비즈니스 요구사항**
- 실시간 좌석 상태 확인이 필요한 예약 시스템
- 많은 사용자가 동시에 콘서트 정보를 조회
- 빠른 응답 속도로 사용자 경험 향상 필요

### 캐싱 대상 선정 기준

| 기준 | 설명 | 적용 예시 |
|------|------|-----------|
| 읽기 빈도 > 쓰기 빈도 | 조회가 수정보다 훨씬 많은 데이터 | 콘서트 정보, 사용자 정보 |
| 데이터 크기 적절 | 메모리 효율성을 위한 적당한 크기 | 좌석 상태, 간단한 설정값 |
| 일정 시간 불변 | 짧은 시간 동안 변경되지 않는 데이터 | 콘서트 스케줄, 가격 정보 |
| 응답 속도 중요 | 빠른 조회가 필수인 데이터 | 인기 콘서트 목록, 실시간 대기열 |

---

## 🔧 캐싱 전략별 구현

### 1️⃣ Look-Aside Pattern (Cache-Aside)

**특징**: 애플리케이션이 캐시와 DB를 직접 관리
**적용 대상**: 콘서트 정보, 좌석 정보
**장점**: 캐시 장애 시에도 서비스 지속 가능

```kotlin
@Service
class ConcertCacheService(
    private val concertRepository: ConcertRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val CONCERT_KEY_PREFIX = "concert:"
        private const val CONCERT_TTL = 3600L // 1시간
    }
    
    fun getConcert(concertId: Long): Concert {
        val cacheKey = "$CONCERT_KEY_PREFIX$concertId"
        
        // 1. 캐시에서 조회
        val cachedData = redisTemplate.opsForValue().get(cacheKey)
        if (cachedData != null) {
            return objectMapper.readValue(cachedData.toString(), Concert::class.java)
        }
        
        // 2. 캐시 미스 시 DB 조회
        val concert = concertRepository.findById(concertId)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다")
        
        // 3. 캐시에 저장
        val jsonData = objectMapper.writeValueAsString(concert)
        redisTemplate.opsForValue().set(cacheKey, jsonData, Duration.ofSeconds(CONCERT_TTL))
        
        return concert
    }
    
    fun evictConcert(concertId: Long) {
        val cacheKey = "$CONCERT_KEY_PREFIX$concertId"
        redisTemplate.delete(cacheKey)
    }
}
```