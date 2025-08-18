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
    private val redisTemplate: RedisTemplate<String, Any>,
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
        lockTimeoutMs: Long = 10000L,
        waitTimeoutMs: Long = 5000L,
        retryIntervalMs: Long = 50L,
        maxRetryCount: Int = 100,
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
        lockTimeoutMs: Long = 10000L,
        waitTimeoutMs: Long = 5000L,
        retryIntervalMs: Long = 50L,
        maxRetryCount: Int = 100,
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
            
            if (elapsed > 1000) { // 1초 이상 걸린 경우 경고
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
        
        while (System.currentTimeMillis() - startTime < waitTimeoutMs && retryCount < maxRetryCount) {
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
            
            val adaptiveBackoff = calculateAdaptiveBackoff(retryCount, backoffMs, retryIntervalMs)
            Thread.sleep(adaptiveBackoff)
            
            backoffMs = minOf(backoffMs * 2, 500L)
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
            retryCount <= 5 -> baseInterval
            retryCount <= 15 -> currentBackoff
            else -> minOf(currentBackoff + baseInterval, 1000L)
        }
    }
    
    private fun tryAcquireLock(key: String, value: String, timeoutMs: Long): Boolean {
        val result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofMillis(timeoutMs))
        return result ?: false
    }
    
    private fun releaseLock(key: String, value: String) {
        try {
            val script = """
                if redis.call("get", KEYS[1]) == ARGV[1] then
                    redis.call("del", KEYS[1])
                    redis.call("publish", "lock:release:" .. KEYS[1], "released")
                    return 1
                else
                    return 0
                end
            """.trimIndent()
            
            redisTemplate.execute<Long?> { connection ->
                connection.eval(
                    script.toByteArray(),
                    org.springframework.data.redis.connection.ReturnType.INTEGER,
                    1,
                    key.toByteArray(),
                    value.toByteArray()
                ) as? Long
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to release lock: $key", e)
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
}

data class LockStatistics(
    val acquisitionCount: Int,
    val failureCount: Int,
    val totalWaitTimeMs: Int,
    val averageWaitTimeMs: Int,
    val successRate: Double
)
