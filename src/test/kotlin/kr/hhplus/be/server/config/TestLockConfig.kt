package kr.hhplus.be.server.config

import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockStrategy
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit

@TestConfiguration
@Profile("test")
class TestLockConfig {

    @Bean
    @Primary
    fun testDistributedLock(): DistributedLock = TestDistributedLock()
}

class TestDistributedLock : DistributedLock {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    
    override fun <T> executeWithLock(
        key: String,
        strategy: LockStrategy,
        waitTimeoutMs: Long,
        retryIntervalMs: Long,
        maxRetryCount: Int,
        action: () -> T
    ): T {
        val lock = locks.computeIfAbsent(key) { ReentrantLock() }
        
        val acquired = try {
            lock.tryLock(waitTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        
        if (!acquired) {
            throw RuntimeException("Failed to acquire lock for key: $key within ${waitTimeoutMs}ms")
        }
        
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}