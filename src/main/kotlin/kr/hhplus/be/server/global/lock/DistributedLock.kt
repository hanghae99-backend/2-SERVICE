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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Component
class DistributedLock(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val redisMessageListenerContainer: RedisMessageListenerContainer?
) {
    private val logger = LoggerFactory.getLogger(DistributedLock::class.java)
    
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
        return when (strategy) {
            LockStrategy.SIMPLE -> executeWithSimpleLock(lockKeys, lockTimeoutMs, action)
            LockStrategy.SPIN -> executeWithSpinLock(lockKeys, lockTimeoutMs, waitTimeoutMs, retryIntervalMs, maxRetryCount, action)
            LockStrategy.PUB_SUB -> {
                if (redisMessageListenerContainer == null) {
                    executeWithSpinLock(lockKeys, lockTimeoutMs, waitTimeoutMs, retryIntervalMs, maxRetryCount, action)
                } else {
                    executeWithPubSubLock(lockKeys, lockTimeoutMs, waitTimeoutMs, action)
                }
            }
        }
    }
    
    private fun <T> executeWithSimpleLock(
        lockKeys: List<String>,
        lockTimeoutMs: Long,
        action: () -> T
    ): T {
        val sortedKeys = lockKeys.sorted()
        val lockValues = sortedKeys.associateWith { UUID.randomUUID().toString() }
        val acquiredLocks = mutableListOf<String>()
        
        try {
            for (key in sortedKeys) {
                if (tryAcquireLock(key, lockValues[key]!!, lockTimeoutMs)) {
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
    
    private fun <T> executeWithSpinLock(
        lockKeys: List<String>,
        lockTimeoutMs: Long,
        waitTimeoutMs: Long,
        retryIntervalMs: Long,
        maxRetryCount: Int,
        action: () -> T
    ): T {
        val sortedKeys = lockKeys.sorted()
        val lockValues = sortedKeys.associateWith { UUID.randomUUID().toString() }
        val startTime = System.currentTimeMillis()
        var retryCount = 0
        
        while (System.currentTimeMillis() - startTime < waitTimeoutMs && retryCount < maxRetryCount) {
            val acquiredLocks = mutableListOf<String>()
            
            try {
                for (key in sortedKeys) {
                    if (tryAcquireLock(key, lockValues[key]!!, lockTimeoutMs)) {
                        acquiredLocks.add(key)
                    } else {
                        releaseLocks(acquiredLocks, lockValues)
                        break
                    }
                }
                
                if (acquiredLocks.size == sortedKeys.size) {
                    return try {
                        action()
                    } finally {
                        releaseLocks(sortedKeys, lockValues)
                    }
                }
                
            } catch (e: Exception) {
                releaseLocks(acquiredLocks, lockValues)
                throw e
            }
            
            retryCount++
            Thread.sleep(retryIntervalMs)
        }
        
        throw ConcurrentAccessException("Spin Lock 획득 실패: $sortedKeys")
    }
    
    private fun <T> executeWithPubSubLock(
        lockKeys: List<String>,
        lockTimeoutMs: Long,
        waitTimeoutMs: Long,
        action: () -> T
    ): T {
        val sortedKeys = lockKeys.sorted()
        val lockValues = sortedKeys.associateWith { UUID.randomUUID().toString() }
        
        if (tryAcquireAllLocks(sortedKeys, lockValues, lockTimeoutMs)) {
            return try {
                action()
            } finally {
                releaseLocks(sortedKeys, lockValues)
            }
        }
        
        return waitForLockWithPubSub(sortedKeys, lockValues, lockTimeoutMs, waitTimeoutMs, action)
    }
    
    private fun <T> waitForLockWithPubSub(
        lockKeys: List<String>,
        lockValues: Map<String, String>,
        lockTimeoutMs: Long,
        waitTimeoutMs: Long,
        action: () -> T
    ): T {
        val latch = CountDownLatch(1)
        val listeners = mutableListOf<MessageListener>()
        var result: T? = null
        var exception: Exception? = null
        
        try {
            lockKeys.forEach { key ->
                val channelPattern = PatternTopic("lock:release:$key")
                val listener = MessageListener { _: Message, _: ByteArray? ->
                    if (tryAcquireAllLocks(lockKeys, lockValues, lockTimeoutMs)) {
                        try {
                            result = action()
                        } catch (e: Exception) {
                            exception = e
                        } finally {
                            releaseLocks(lockKeys, lockValues)
                            latch.countDown()
                        }
                    }
                }
                
                redisMessageListenerContainer!!.addMessageListener(listener, channelPattern)
                listeners.add(listener)
            }
            
            if (tryAcquireAllLocks(lockKeys, lockValues, lockTimeoutMs)) {
                return try {
                    action()
                } finally {
                    releaseLocks(lockKeys, lockValues)
                }
            }
            
            val acquired = latch.await(waitTimeoutMs, TimeUnit.MILLISECONDS)
            
            if (!acquired) {
                throw ConcurrentAccessException("Pub/Sub Lock 대기 시간 초과: $lockKeys")
            }
            
            exception?.let { throw it }
            
            return result ?: throw IllegalStateException("결과가 null입니다")
            
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
    
    private fun tryAcquireAllLocks(
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
    
    private fun tryAcquireLock(key: String, value: String, timeoutMs: Long): Boolean {
        val result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofMillis(timeoutMs))
        return result ?: false
    }
    
    private fun releaseLock(key: String, value: String) {
        try {
            val currentValue = redisTemplate.opsForValue().get(key) as? String
            
            if (currentValue == value) {
                val deleted = redisTemplate.delete(key)
                
                if (deleted) {
                    redisTemplate.convertAndSend("lock:release:$key", "released")
                }
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
}
