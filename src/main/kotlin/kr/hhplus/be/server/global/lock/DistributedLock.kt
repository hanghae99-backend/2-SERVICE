package kr.hhplus.be.server.global.lock

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

@Component
class DistributedLock(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    fun <T> executeWithLock(
        lockKey: String,
        lockTimeoutMs: Long = 10000L,
        waitTimeoutMs: Long = 5000L,
        action: () -> T
    ): T {
        return executeWithMultiLock(
            lockKeys = listOf(lockKey),
            lockTimeoutMs = lockTimeoutMs,
            waitTimeoutMs = waitTimeoutMs,
            action = action
        )
    }
    
    fun <T> executeWithMultiLock(
        lockKeys: List<String>,
        lockTimeoutMs: Long = 10000L,
        waitTimeoutMs: Long = 5000L,
        action: () -> T
    ): T {
        // 데드락 방지를 위해 키를 정렬
        val sortedKeys = lockKeys.sorted()
        val lockValues = sortedKeys.associateWith { UUID.randomUUID().toString() }
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < waitTimeoutMs) {
            val acquiredLocks = mutableListOf<String>()
            
            try {
                // 정렬된 순서로 락 획득
                for (key in sortedKeys) {
                    if (tryAcquireLock(key, lockValues[key]!!, lockTimeoutMs)) {
                        acquiredLocks.add(key)
                    } else {
                        // 일부 락 획득 실패 시 이미 획득한 락들 해제
                        releaseLocks(acquiredLocks, lockValues)
                        break
                    }
                }
                
                // 모든 락 획득 성공
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
            
            Thread.sleep(50)
        }
        
        throw ConcurrentAccessException("멀티락 획득에 실패했습니다: $sortedKeys")
    }
    
    private fun tryAcquireLock(key: String, value: String, timeoutMs: Long): Boolean {
        // Redis SET NX EX: 키가 없을 때만 설정 (원자적 락 획득)
        val result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofMillis(timeoutMs))
        return result ?: false
    }
    
    private fun releaseLock(key: String, value: String) {
        // Lua 스크립트로 소유권 확인 후 락 해제 (원자적 처리)
        val script = """
            if redis.call("GET", KEYS[1]) == ARGV[1] then
                return redis.call("DEL", KEYS[1])
            else
                return 0
            end
        """.trimIndent()
        
        try {
            redisTemplate.execute(
                RedisScript.of(script, Long::class.java),
                listOf(key),
                value
            )
        } catch (e: Exception) {
            // 락 해제 실패 시 TTL로 자동 해제되므로 안전
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
