# STEP13: 콘서트 매진 랭킹 시스템 설계 및 구현 보고서

## 📋 개요

콘서트 예약 시스템에서 실시간 매진 랭킹 기능을 Redis 기반으로 설계하고 구현한 프로젝트입니다. 사용자에게 인기 콘서트 정보를 빠르게 제공하여 서비스 경험을 향상시키는 것이 목표입니다.

## 🎯 요구사항 분석

### 기능적 요구사항
- 콘서트별 예약 수 기반 실시간 랭킹 제공
- 예약 발생 시 즉시 랭킹 반영
- 예약 취소 시 랭킹 감소 처리
- 상위 N개 콘서트 조회 API 제공

## 🏗️ 시스템 아키텍처 설계

### 전체 아키텍처
```
[Client] → [API Controller] → [Service Layer] → [Redis ZSet + DB]
    ↓              ↓              ↓              ↓
 랭킹 요청    비즈니스 로직    캐시 조회     데이터 저장소
```

### 핵심 컴포넌트
1. **SelloutRankingService**: 랭킹 비즈니스 로직
2. **RedisTemplate**: Redis 연산 추상화
3. **ReservationScheduler**: 정기적 랭킹 재구축
4. **ConcertController**: REST API 엔드포인트

## 🔧 기술적 설계 결정

### Redis ZSet (Sorted Set) 선택 근거

#### 자료구조 비교분석
| 자료구조 | 장점 | 단점 | 적합성 |
|---------|------|------|--------|
| **ZSet** | 자동 정렬, O(log N) 삽입/조회, 범위 조회 지원 | 메모리 사용량 높음 | ✅ **최적** |
| Hash | O(1) 조회, 메모리 효율적 | 정렬 불가, 별도 정렬 필요 | ❌ 부적합 |
| List | 순서 보장, 간단한 구조 | 정렬 비용 O(N log N) | ❌ 비효율 |

#### ZSet 선택 이유
```kotlin
// 1. 실시간 자동 정렬
redisTemplate.opsForZSet().incrementScore(SELLOUT_RANKING_KEY, concertId.toString(), 1.0)

// 2. O(log N) 성능으로 상위 N개 조회
redisTemplate.opsForZSet().reverseRange(SELLOUT_RANKING_KEY, 0, limit.toLong() - 1)

// 3. 점수 기반 범위 조회 지원
redisTemplate.opsForZSet().reverseRangeByScore(key, min, max)
```

### 캐싱 전략 설계

#### 1. Cache-Aside Pattern 적용
```kotlin
@Cacheable(value = ["concerts:sellout:main"], key = "#limit")
fun getSelloutRanking(limit: Int = 10): List<SelloutRankingDto> {
    // 1차: Redis ZSet 조회
    val rankingIds = redisTemplate.opsForZSet()
        .reverseRange(SELLOUT_RANKING_KEY, 0, limit.toLong() - 1)
    
    // 2차: 실패 시 DB Fallback
    if (rankingIds.isEmpty()) {
        return getFallbackRanking(limit)
    }
    
    return buildSelloutRankingDtos(rankingIds)
}
```

#### 2. Write-Through 전략
- Redis 업데이트와 동시에 DB 정합성 유지
- 이벤트 기반 비동기 처리로 성능 최적화

#### 3. TTL 기반 캐시 갱신
```kotlin
companion object {
    private const val SELLOUT_RANKING_KEY = "concerts:sellout_ranking"
    private const val RANKING_VERSION_KEY = "concerts:ranking:version"
}

// 버전 기반 원자적 교체
fun rebuildSelloutRanking() {
    val newVersion = System.currentTimeMillis()
    val tempKey = "${SELLOUT_RANKING_KEY}:${newVersion}"
    
    // 임시 키에 데이터 구축 후 원자적 교체
    redisTemplate.rename(tempKey, SELLOUT_RANKING_KEY)
}
```

## 💻 핵심 구현 코드

### 1. 실시간 랭킹 업데이트
```kotlin
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
```

### 2. 스케줄러 기반 배치 재구축
```kotlin
@Scheduled(fixedRateString = "\${app.scheduler.sellout.ranking.interval:300000}")
fun rebuildSelloutRanking() {
    distributedLock.executeWithLock(
        lockKey = "scheduler:sellout:ranking",
        lockTimeoutMs = 240000L,
        waitTimeoutMs = 30000L
    ) {
        // 최근 1시간 예약 데이터로 랭킹 재구축
        val oneHourAgo = LocalDateTime.now().minusHours(1)
        val reservationCounts = reservationRepository.countReservationsByHour(oneHourAgo)
        
        reservationCounts.forEach { (concertId, count) ->
            redisTemplate.opsForZSet().add(tempKey, concertId.toString(), count.toDouble())
        }
    }
}
```

### 3. Fallback 메커니즘
```kotlin
private fun getFallbackRanking(limit: Int): List<SelloutRankingDto> {
    return concertRepository.findByIsActiveTrue().take(limit)
        .mapIndexed { index, concert ->
            SelloutRankingDto.from(
                concert = concert,
                reservationCount = 0, // Redis 장애 시 0으로 표시
                ranking = index + 1
            )
        }
}
```

## 🧪 테스트 전략

### 1. 단위 테스트
```kotlin
class SelloutRankingServiceTest : StringSpec({
    "랭킹 조회 시 상위 N개 콘서트를 반환해야 한다" {
        // Given
        val limit = 5
        setupMockRanking()
        
        // When
        val result = selloutRankingService.getSelloutRanking(limit)
        
        // Then
        result shouldHaveSize limit
        result.first().ranking shouldBe 1
    }
})
```

### 2. 통합 테스트
```kotlin
class SelloutRankingIntegrationTest : AbstractIntegrationTest() {
    "예약 발생 시 랭킹이 실시간 업데이트되어야 한다" {
        // Given
        val concert = createTestConcert()
        
        // When
        reservationService.reserve(userId, concertId, seatId)
        
        // Then
        eventually(Duration.ofSeconds(5)) {
            val ranking = selloutRankingService.getSelloutRanking(10)
            ranking.first().concertId shouldBe concertId
        }
    }
}
```

## 🚨 장애 대응

### 1. Redis 장애 시나리오
```kotlin
// Circuit Breaker Pattern 적용
private fun getWithFallback(limit: Int): List<SelloutRankingDto> {
    return try {
        getFromRedis(limit)
    } catch (RedisConnectionException e) {
        logger.warn("Redis 연결 실패, DB Fallback 적용", e)
        getFallbackRanking(limit)
    }
}
```
