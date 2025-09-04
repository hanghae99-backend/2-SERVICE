package kr.hhplus.be.server.global.lock

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

@Component
class DistributedLock(
    private val redisTemplate: RedisTemplate<String, Any>?,
    private val redisMessageListenerContainer: RedisMessageListenerContainer?
) {
    private val logger = LoggerFactory.getLogger(DistributedLock::class.java)
    
    // 성능 모니터링을 위한 메트릭
    private val lockAcquisitionCount = AtomicInteger(0)
    private val lockFailureCount = AtomicInteger(0)
    private val totalWaitTime = AtomicInteger(0)
    
    fun <T> executeWithLock(
        lockKey: String,
        strategy: LockStrategy = LockStrategy.SPIN,
        lockTimeoutMs: Long = 3000L,
        waitTimeoutMs: Long = 1000L,
        retryIntervalMs: Long = 10L,
        maxRetryCount: Int = 50,
        action: () -> T
    ): T {
        return executeWithMultiLock(
            lockKeys = listOf(lockKey),
            strategy = strategy,
            lockTimeoutMs = lockTimeoutMs,
            waitTimeoutMs = waitTimeoutMs,
            retryIntervalMs = retryIntervalMs,
            maxRetryCount = maxRetryCount,
            action = action
        )
    }
    
    fun <T> executeWithMultiLock(
        lockKeys: List<String>,
        strategy: LockStrategy = LockStrategy.SPIN,
        lockTimeoutMs: Long = 3000L,
        waitTimeoutMs: Long = 1000L,
        retryIntervalMs: Long = 10L,
        maxRetryCount: Int = 50,
        action: () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = when (strategy) {
                LockStrategy.SIMPLE -> executeWithSimpleLock(lockKeys, lockTimeoutMs, action)
                LockStrategy.SPIN -> executeWithAdaptiveSpinLock(lockKeys, lockTimeoutMs, waitTimeoutMs, retryIntervalMs, maxRetryCount, action)
                LockStrategy.PUB_SUB -> {
                    if (redisMessageListenerContainer == null) {
                        executeWithAdaptiveSpinLock(lockKeys, lockTimeoutMs, waitTimeoutMs, retryIntervalMs, maxRetryCount, action)
                    } else {
                        executeWithOptimizedPubSubLock(lockKeys, lockTimeoutMs, waitTimeoutMs, action)
                    }
                }
            }
            
            lockAcquisitionCount.incrementAndGet()
            result
            
        } catch (e: Exception) {
            lockFailureCount.incrementAndGet()
            throw e
        } finally {
            val elapsed = System.currentTimeMillis() - startTime
            totalWaitTime.addAndGet(elapsed.toInt())
            
            if (elapsed > 500) { // 500ms 이상 걸린 경우 경고
                logger.warn("🐌 락 처리 시간 초과: {}ms, keys: {}, strategy: {}", elapsed, lockKeys, strategy)
            }
        }
    }
    
    private fun <T> executeWithSimpleLock(
        lockKeys: List<String>,
        lockTimeoutMs: Long,
        action: () -> T
    ): T {
        val sortedKeys = lockKeys.sorted()
        val lockValues = sortedKeys.associateWith { generateLockValue() }
        val acquiredLocks = mutableListOf<String>()
        
        try {
            for (key in sortedKeys) {
                val acquired = tryAcquireLockWithFastFail(key, lockValues[key]!!, lockTimeoutMs)
                if (acquired) {
                    acquiredLocks.add(key)
                } else {
                    releaseLocks(acquiredLocks, lockValues)
                    throw ConcurrentAccessException("Simple Lock 획득 실패: $key")
                }
            }
            
            return action()
            
        } finally {
            releaseLocks(sortedKeys, lockValues)
        }
    }
    
    private fun <T> executeWithAdaptiveSpinLock(
        lockKeys: List<String>,
        lockTimeoutMs: Long,
        waitTimeoutMs: Long,
        retryIntervalMs: Long,
        maxRetryCount: Int,
        action: () -> T
    ): T {
        val sortedKeys = lockKeys.sorted()
        val lockValues = sortedKeys.associateWith { generateLockValue() }
        val startTime = System.currentTimeMillis()
        var retryCount = 0
        var backoffMs = retryIntervalMs
        
        while (retryCount < maxRetryCount) {
            // 시간 체크를 먼저 수행
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= waitTimeoutMs) {
                break
            }
            
            val acquiredLocks = mutableListOf<String>()
            var allAcquired = true
            
            try {
                for (key in sortedKeys) {
                    if (tryAcquireLock(key, lockValues[key]!!, lockTimeoutMs)) {
                        acquiredLocks.add(key)
                    } else {
                        allAcquired = false
                        break
                    }
                }
                
                if (allAcquired) {
                    return try {
                        action()
                    } finally {
                        releaseLocks(sortedKeys, lockValues)
                    }
                } else {
                    releaseLocks(acquiredLocks, lockValues)
                }
                
            } catch (e: Exception) {
                releaseLocks(acquiredLocks, lockValues)
                throw e
            }
            
            retryCount++
            
            // 남은 시간 계산
            val remainingTime = waitTimeoutMs - (System.currentTimeMillis() - startTime)
            if (remainingTime <= 0) {
                break
            }
            
            val adaptiveBackoff = calculateAdaptiveBackoff(retryCount, backoffMs, retryIntervalMs)
            val sleepTime = minOf(adaptiveBackoff, remainingTime)
            
            if (sleepTime > 0) {
                Thread.sleep(sleepTime)
            }
            
            backoffMs = minOf(backoffMs * 2, 100L)
        }
        
        throw ConcurrentAccessException("Adaptive Spin Lock 획득 실패: $sortedKeys (retries: $retryCount)")
    }
    
    private fun <T> executeWithOptimizedPubSubLock(
        lockKeys: List<String>,
        lockTimeoutMs: Long,
        waitTimeoutMs: Long,
        action: () -> T
    ): T {
        val sortedKeys = lockKeys.sorted()
        val lockValues = sortedKeys.associateWith { generateLockValue() }
        
        if (tryAcquireAllLocksAtomic(sortedKeys, lockValues, lockTimeoutMs)) {
            return try {
                action()
            } finally {
                releaseLocks(sortedKeys, lockValues)
            }
        }
        
        return waitForLockWithOptimizedPubSub(sortedKeys, lockValues, lockTimeoutMs, waitTimeoutMs, action)
    }
    
    private fun <T> waitForLockWithOptimizedPubSub(
        lockKeys: List<String>,
        lockValues: Map<String, String>,
        lockTimeoutMs: Long,
        waitTimeoutMs: Long,
        action: () -> T
    ): T {
        val latch = CountDownLatch(1)
        val listeners = mutableListOf<MessageListener>()
        val result = CompletableFuture<T>()
        
        try {
            lockKeys.forEach { key ->
                val listener = createOptimizedListener(key, lockKeys, lockValues, lockTimeoutMs, action, result, latch)
                val channelPattern = PatternTopic("lock:release:$key")
                
                redisMessageListenerContainer!!.addMessageListener(listener, channelPattern)
                listeners.add(listener)
            }
            
            if (tryAcquireAllLocksAtomic(lockKeys, lockValues, lockTimeoutMs)) {
                return try {
                    action()
                } finally {
                    releaseLocks(lockKeys, lockValues)
                }
            }
            
            val acquired = latch.await(waitTimeoutMs, TimeUnit.MILLISECONDS)
            
            if (!acquired) {
                throw ConcurrentAccessException("Optimized Pub/Sub Lock 대기 시간 초과: $lockKeys")
            }
            
            return result.get(1000, TimeUnit.MILLISECONDS)
            
        } finally {
            listeners.forEach { listener ->
                try {
                    redisMessageListenerContainer!!.removeMessageListener(listener)
                } catch (e: Exception) {
                    logger.warn("Failed to remove message listener", e)
                }
            }
        }
    }
    
    private fun <T> createOptimizedListener(
        triggerKey: String,
        lockKeys: List<String>,
        lockValues: Map<String, String>,
        lockTimeoutMs: Long,
        action: () -> T,
        result: CompletableFuture<T>,
        latch: CountDownLatch
    ): MessageListener {
        return MessageListener { _: Message, _: ByteArray? ->
            if (latch.count > 0 && tryAcquireAllLocksAtomic(lockKeys, lockValues, lockTimeoutMs)) {
                try {
                    val actionResult = action()
                    result.complete(actionResult)
                } catch (e: Exception) {
                    result.completeExceptionally(e)
                } finally {
                    releaseLocks(lockKeys, lockValues)
                    latch.countDown()
                }
            }
        }
    }
    
    private fun tryAcquireLockWithFastFail(key: String, value: String, timeoutMs: Long): Boolean {
        repeat(3) { attempt ->
            if (tryAcquireLock(key, value, timeoutMs)) {
                return true
            }
            if (attempt < 2) {
                Thread.sleep(10L * (attempt + 1))
            }
        }
        return false
    }
    
    private fun tryAcquireAllLocksAtomic(
        lockKeys: List<String>,
        lockValues: Map<String, String>,
        lockTimeoutMs: Long
    ): Boolean {
        val acquiredLocks = mutableListOf<String>()
        
        try {
            for (key in lockKeys) {
                if (tryAcquireLock(key, lockValues[key]!!, lockTimeoutMs)) {
                    acquiredLocks.add(key)
                } else {
                    releaseLocks(acquiredLocks, lockValues)
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            releaseLocks(acquiredLocks, lockValues)
            return false
        }
    }
    
    private fun calculateAdaptiveBackoff(retryCount: Int, currentBackoff: Long, baseInterval: Long): Long {
        return when {
            retryCount <= 5 -> baseInterval  // 처음 5회는 빠르게 재시도
            retryCount <= 15 -> minOf((currentBackoff * 1.2).toLong(), 50L)  // 점진적 증가
            retryCount <= 30 -> minOf((currentBackoff * 1.1).toLong(), 100L)  // 더 느리게 증가
            else -> minOf(currentBackoff, 200L)  // 최대 200ms 대기
        }
    }
    
    private fun tryAcquireLock(key: String, value: String, timeoutMs: Long): Boolean {
        return try {
            redisTemplate?.let { template ->
                val result = template.opsForValue()
                    .setIfAbsent(key, value, Duration.ofMillis(timeoutMs))
                result ?: false
            } ?: false
        } catch (e: Exception) {
            logger.warn("Redis 락 획득 중 오류 발생: key=$key, error=${e.message}")
            false
        }
    }
    
    private fun releaseLock(key: String, value: String) {
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount < maxRetries) {
            try {
                val result = redisTemplate?.let { template ->
                    val script = """
                        if redis.call("get", KEYS[1]) == ARGV[1] then
                            redis.call("del", KEYS[1])
                            redis.call("publish", "lock:release:" .. KEYS[1], "released")
                            return 1
                        else
                            return 0
                        end
                    """.trimIndent()
                    
                    template.execute<Long?> { connection ->
                        connection.eval(
                            script.toByteArray(),
                            org.springframework.data.redis.connection.ReturnType.INTEGER,
                            1,
                            key.toByteArray(),
                            value.toByteArray()
                        ) as? Long
                    }
                } ?: 0L
                
                if (result != null && result > 0) {
                    break // 성공적으로 해제됨
                } else if (retryCount == 0) {
                    logger.debug("락이 이미 해제되었거나 다른 스레드에 의해 소유됨: $key")
                    break // 락이 이미 없거나 소유권이 없음 - 정상 상황
                }
                
            } catch (e: Exception) {
                logger.warn("락 해제 중 오류 발생 (시도 ${retryCount + 1}/$maxRetries): key=$key, error=${e.message}")
                retryCount++
                
                if (retryCount < maxRetries) {
                    Thread.sleep(50L * retryCount) // 재시도 전 대기
                } else {
                    logger.error("락 해제 최종 실패: $key - 수동 정리 필요할 수 있음")
                }
            }
        }
    }
    
    private fun releaseLocks(keys: List<String>, lockValues: Map<String, String>) {
        keys.forEach { key ->
            lockValues[key]?.let { value ->
                releaseLock(key, value)
            }
        }
    }
    
    private fun generateLockValue(): String {
        return "${UUID.randomUUID()}-${Thread.currentThread().id}-${System.currentTimeMillis()}"
    }
    
    // 성능 모니터링 메서드
    fun getLockStatistics(): LockStatistics {
        return LockStatistics(
            acquisitionCount = lockAcquisitionCount.get(),
            failureCount = lockFailureCount.get(),
            totalWaitTimeMs = totalWaitTime.get(),
            averageWaitTimeMs = if (lockAcquisitionCount.get() > 0) {
                totalWaitTime.get() / lockAcquisitionCount.get()
            } else 0,
            successRate = if (lockAcquisitionCount.get() + lockFailureCount.get() > 0) {
                lockAcquisitionCount.get().toDouble() / (lockAcquisitionCount.get() + lockFailureCount.get()) * 100
            } else 0.0
        )
    }
    
    fun resetStatistics() {
        lockAcquisitionCount.set(0)
        lockFailureCount.set(0)
        totalWaitTime.set(0)
    }
    
    fun clearAllLocks() {
        try {
            redisTemplate?.let { template ->
                val keys = template.keys("lock:*")
                if (keys.isNotEmpty()) {
                    template.delete(keys)
                    logger.info("🧯 모든 락 정리 완료: {}개", keys.size)
                }
            }
        } catch (e: Exception) {
            logger.warn("락 정리 중 오류 발생", e)
        }
    }
    
    fun getLockCount(): Int {
        return try {
            redisTemplate?.keys("lock:*")?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
}

data class LockStatistics(
    val acquisitionCount: Int,
    val failureCount: Int,
    val totalWaitTimeMs: Int,
    val averageWaitTimeMs: Int,
    val successRate: Double
)
