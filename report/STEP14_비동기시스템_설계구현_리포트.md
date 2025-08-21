# STEP14: Redis 기반 비동기 시스템 설계 및 구현 보고서

## 📋 개요

콘서트 예약 시스템에서 Redis를 활용한 비동기 처리 시스템을 설계하고 구현한 프로젝트입니다. Redis의 다양한 자료구조(Hash, ZSet)를 활용하여 대기열 관리, 토큰 라이프사이클, 스케줄링을 비동기로 처리하여 시스템 성능과 확장성을 향상시키는 것이 목표입니다.

## 🎯 요구사항 분석

### 기능적 요구사항
- Redis Hash 기반 활성 토큰 관리 및 만료 처리
- Redis ZSet 기반 대기열 순서 관리 및 자동 순환
- Redis 스케줄링을 통한 시스템 리소스 정리
- 분산 락을 활용한 다중 인스턴스 동시성 제어

## 🏗️ Redis 기반 비동기 아키텍처 설계

### 전체 시스템 아키텍처
```
[Client Request] → [Sync API] → [Redis Operations] → [Background Schedulers]
       ↓              ↓             ↓                    ↓
   즉시 응답     Redis 상태 변경   비동기 업데이트     백그라운드 처리
                - 토큰 활성화    - @Async 메서드        - 만료 토큰 정리
                - 대기열 추가    - ZSet 점수 업데이트   - 대기열 순환
                - 랭킹 업데이트  - Hash 상태 변경       - 데이터 정합성
```

### Redis 기반 비동기 구성요소
1. **Redis Hash**: 활성 토큰 상태 관리 (`active_tokens_hash`)
2. **Redis ZSet**: 대기열 순서 관리 (`waiting_queue_zset`)
3. **Redis Scheduler**: 분산 락 기반 정기 작업
4. **@Async Methods**: Redis 연산 비동기 처리

## 🔧 핵심 설계 결정사항

### 1. Redis 자료구조 기반 비동기 시스템

#### Hash vs ZSet vs List 비교분석
| 용도 | 자료구조 | 장점 | 단점 | 선택 |
|------|---------|------|------|------|
| **활성 토큰** | Hash | O(1) 조회, 만료시간 저장 | 정렬 불가 | ✅ Hash |
| **대기열** | ZSet | 자동 정렬, 순서 보장 | 메모리 사용량 | ✅ ZSet |


#### Redis 기반 비동기 처리 전략
```kotlin
// Hash: 활성 토큰 상태 관리 (만료시간 포함)
redisTemplate.opsForHash<String, String>()
    .put(ACTIVE_TOKENS_HASH, token, expireTime.toString())

// ZSet: 대기열 순서 관리 (시퀀스 기반 정렬)
redisTemplate.opsForZSet()
    .add(WAITING_QUEUE_ZSET, token, sequence.toDouble())
```

### 2. Redis 기반 대기열 시스템 (Hash + ZSet 하이브리드)

#### 기존 단일 자료구조 vs 하이브리드 구조
| 방식 | 장점 | 단점 | 성능 |
|------|------|------|------|
| **ZSet 단독** | 단순함, 순서 보장 | 상태 정보 제한 | O(log N) |
| **Hash 단독** | 빠른 조회, 상태 저장 | 순서 관리 어려움 | O(1) |
| **Hash + ZSet** | 최적 성능, 풍부한 상태 | 복잡성 증가 | O(1) + O(log N) |

#### 하이브리드 구조 설계
```kotlin
companion object {
    // Hash: 활성 토큰 관리 (key: token, value: expireTime)
    private const val ACTIVE_TOKENS_HASH = "active_tokens_hash"
    
    // ZSet: 대기열 순서 관리 (member: token, score: sequence)  
    private const val WAITING_QUEUE_ZSET = "waiting_queue_zset"
    
    // 시퀀스 생성기 (원자적 증가)
    private const val QUEUE_SEQUENCE_KEY = "queue_sequence"
}

// 토큰 상태 확인 - Hash와 ZSet 조합
override fun getTokenStatus(token: String): TokenStatus {
    return when {
        redisTemplate.opsForHash<String, String>().hasKey(ACTIVE_TOKENS_HASH, token) -> TokenStatus.ACTIVE
        redisTemplate.opsForZSet().rank(WAITING_QUEUE_ZSET, token) != null -> TokenStatus.WAITING  
        else -> TokenStatus.EXPIRED
    }
}
```

## 💻 핵심 구현 요소

### 1. Redis Hash 기반 활성 토큰 관리

#### 토큰 상태 비동기 관리
```kotlin
@Component
class RedisTokenStore(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val ACTIVE_TOKENS_HASH = "active_tokens_hash"
        private val ACTIVE_TTL = Duration.ofMinutes(10)
    }
    
    // 토큰 활성화 (비동기)
    override fun activateToken(token: String) {
        val expireTime = System.currentTimeMillis() + ACTIVE_TTL.toMillis()
        redisTemplate.opsForHash<String, String>()
            .put(ACTIVE_TOKENS_HASH, token, expireTime.toString())
    }
    
    // 만료된 토큰 일괄 조회 (백그라운드 스케줄러용)
    override fun findExpiredActiveTokens(): List<String> {
        val activeTokens = redisTemplate.opsForHash<String, String>().entries(ACTIVE_TOKENS_HASH)
        val currentTime = System.currentTimeMillis()
        
        return activeTokens.mapNotNull { (token, expireTimeStr) ->
            val expireTime = expireTimeStr.toLongOrNull() ?: 0L
            if (currentTime >= expireTime) token else null
        }
    }
}
```

### 2. Redis ZSet 기반 대기열 순환 시스템

#### 순서 보장 대기열 구현
```kotlin
class QueueManager(private val tokenStore: TokenStore) {
    
    companion object {
        private const val MAX_ACTIVE_TOKENS = 100L
    }
    
    // 대기열 추가 (시퀀스 기반 순서 보장)
    fun addToQueue(token: String) {
        tokenStore.addToWaitingQueue(token)
    }
    
    // 가용 슬롯 계산 후 자동 활성화
    fun processQueueAutomatically(): Int {
        val availableSlots = calculateAvailableSlots()
        var activatedCount = 0
        
        if (availableSlots > 0) {
            val tokensToActivate = getNextTokensFromQueue(availableSlots)
            tokensToActivate.forEach { tokenString ->
                try {
                    activateToken(tokenString)
                    activatedCount++
                } catch (e: Exception) {
                    logger.error("토큰 활성화 실패: $tokenString", e)
                }
            }
        }
        return activatedCount
    }
    
    private fun calculateAvailableSlots(): Int {
        val currentActiveCount = tokenStore.countActiveTokens()
        return (MAX_ACTIVE_TOKENS - currentActiveCount).toInt()
    }
}
```

### 3. @Async 기반 비동기 Redis 연산

#### 토큰 생명주기 비동기 관리
```kotlin
@Component
class TokenLifecycleManager(
    private val tokenStore: TokenStore,
    private val queueManager: QueueManager
) {
    
    // 만료 토큰 정리 및 대기열 자동 순환 (비동기)
    fun cleanupExpiredTokensAndProcessQueue(): Pair<Int, Int> {
        val expiredTokens = tokenStore.findExpiredActiveTokens()
        var cleanedCount = 0
        
        // 1. 만료된 토큰 일괄 정리
        expiredTokens.forEach { expiredToken ->
            try {
                expireToken(expiredToken)
                cleanedCount++
            } catch (e: Exception) {
                logger.error("토큰 만료 처리 실패: $expiredToken", e)
            }
        }
        
        // 2. 정리된 슬롯만큼 대기열에서 자동 활성화
        val activatedCount = if (cleanedCount > 0) {
            queueManager.processQueueAutomatically()
        } else {
            0
        }
        
        return Pair(cleanedCount, activatedCount)
    }
    
    // 결제 완료 시 즉시 토큰 만료 및 다음 사용자 활성화
    fun completeToken(token: String) {
        expireToken(token)
        queueManager.processQueueAutomatically()
    }
}
```

### 4. 스케줄러 기반 백그라운드 작업

#### 통합 스케줄러 설계
```kotlin
@Component
@ConditionalOnProperty(name = ["app.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class ReservationScheduler(
    private val reservationService: ReservationService,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val selloutRankingService: SelloutRankingService,
    private val distributedLock: DistributedLock
) {
    
    /**
     * 만료된 예약 정리 - 1분마다
     */
    @Scheduled(fixedRateString = "\${app.scheduler.reservation.cleanup.interval:60000}")
    fun cleanupExpiredReservations() {
        distributedLock.executeWithLock("scheduler:reservation:cleanup") {
            val cleanedCount = reservationService.cleanupExpiredReservations()
            if (cleanedCount > 0) {
                logger.info("✅ 만료된 예약 정리 완료: {}건", cleanedCount)
            }
        }
    }
    
    /**
     * 대기열 자동 처리 - 5초마다  
     */
    @Scheduled(fixedRateString = "\${app.scheduler.queue.process.interval:5000}")
    fun processQueue() {
        distributedLock.executeWithLock("scheduler:queue:process") {
            val (expiredCount, activatedCount) = tokenLifecycleManager.cleanupExpiredTokensAndProcessQueue()
            
            if (expiredCount > 0 || activatedCount > 0) {
                logger.info("🔄 대기열 처리: 만료 {}개, 활성화 {}개", expiredCount, activatedCount)
            }
        }
    }
    
}
```

### 5. 분산 락을 활용한 동시성 제어

#### 스케줄러 중복 실행 방지
```kotlin
@Component
class DistributedLock(
    private val redisTemplate: StringRedisTemplate
) {
    
    fun <T> executeWithLock(
        lockKey: String,
        lockTimeoutMs: Long = 30000L,
        waitTimeoutMs: Long = 5000L,
        operation: () -> T
    ): T {
        val lockValue = UUID.randomUUID().toString()
        val acquired = acquireLock(lockKey, lockValue, lockTimeoutMs)
        
        if (!acquired) {
            throw LockAcquisitionException("락 획득 실패: $lockKey")
        }
        
        try {
            return operation()
        } finally {
            releaseLock(lockKey, lockValue)
        }
    }
    
    private fun acquireLock(key: String, value: String, timeoutMs: Long): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofMillis(timeoutMs)) ?: false
    }
}
```

## 🧪 테스트 전략

### 1. 이벤트 발행/처리 테스트
```kotlin
class EventHandlingTest : AbstractIntegrationTest() {
    
    @MockK
    lateinit var selloutRankingService: SelloutRankingService
    
    "예약 생성 시 매진 랭킹 업데이트 이벤트가 비동기 처리되어야 한다" {
        // Given
        val reservationEvent = ReservationCreatedEvent(1L, 1L, 1L, 1L, BigDecimal(100), LocalDateTime.now())
        
        // When
        eventPublisher.publishEvent(reservationEvent)
        
        // Then - 비동기 처리 완료 대기
        eventually(Duration.ofSeconds(5)) {
            verify { selloutRankingService.incrementReservationCount(1L) }
        }
    }
}
```

### 2. 동시성 테스트
```kotlin
class AsyncConcurrencyTest : AbstractIntegrationTest() {
    
    "대량 이벤트 발생 시 스레드 풀이 안정적으로 처리해야 한다" {
        // Given
        val eventCount = 1000
        val events = (1..eventCount).map { 
            ReservationCreatedEvent(it.toLong(), 1L, it.toLong(), it.toLong(), BigDecimal(100), LocalDateTime.now())
        }
        
        // When - 동시에 대량 이벤트 발행
        val futures = events.map { event ->
            CompletableFuture.runAsync { eventPublisher.publishEvent(event) }
        }
        
        CompletableFuture.allOf(*futures.toTypedArray()).get(30, TimeUnit.SECONDS)
        
        // Then - 모든 이벤트 처리 확인
        eventually(Duration.ofSeconds(10)) {
            verify(exactly = eventCount) { selloutRankingService.incrementReservationCount(any()) }
        }
    }
}
```
