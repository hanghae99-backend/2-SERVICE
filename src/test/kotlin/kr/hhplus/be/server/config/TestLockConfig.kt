package kr.hhplus.be.server.config

import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockStrategy
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit

@TestConfiguration
@Profile("test")
class TestLockConfig {

    @Bean
    @Primary
    fun testDistributedLock(
        redisTemplate: RedisTemplate<String, Any>,
        redisMessageListenerContainer: RedisMessageListenerContainer?
    ): DistributedLock = TestDistributedLock(redisTemplate, redisMessageListenerContainer)
}

class TestDistributedLock(
    redisTemplate: RedisTemplate<String, Any>,
    redisMessageListenerContainer: RedisMessageListenerContainer?
) : DistributedLock(redisTemplate, redisMessageListenerContainer) {
    
    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    
    override fun <T> executeWithLock(
        lockKey: String,
        strategy: LockStrategy,
        lockTimeoutMs: Long,
        waitTimeoutMs: Long,
        retryIntervalMs: Long,
        maxRetryCount: Int,
        action: () -> T
    ): T {
        val lock = locks.computeIfAbsent(lockKey) { ReentrantLock() }
        
        val acquired = try {
            lock.tryLock(waitTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        
        if (!acquired) {
            throw RuntimeException("Failed to acquire lock for key: $lockKey within ${waitTimeoutMs}ms")
        }
        
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
    
    override fun <T> executeWithMultiLock(
        lockKeys: List<String>,
        strategy: LockStrategy,
        lockTimeoutMs: Long,
        waitTimeoutMs: Long,
        retryIntervalMs: Long,
        maxRetryCount: Int,
        action: () -> T
    ): T {
        // 멀티 락을 위한 테스트 구현
        val sortedKeys = lockKeys.sorted()
        val acquiredLocks = mutableListOf<Pair<String, ReentrantLock>>()
        
        try {
            // 데드락 방지를 위해 정렬된 순서로 락 획득
            for (key in sortedKeys) {
                val lock = locks.computeIfAbsent(key) { ReentrantLock() }
                
                val acquired = try {
                    lock.tryLock(waitTimeoutMs, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    false
                }
                
                if (acquired) {
                    acquiredLocks.add(key to lock)
                } else {
                    // 실패 시 이미 획득한 락들 해제
                    acquiredLocks.forEach { (_, l) -> l.unlock() }
                    throw RuntimeException("Failed to acquire lock for key: $key within ${waitTimeoutMs}ms")
                }
            }
            
            return action()
            
        } finally {
            // 역순으로 락 해제
            acquiredLocks.reversed().forEach { (_, lock) ->
                lock.unlock()
            }
        }
    }
}