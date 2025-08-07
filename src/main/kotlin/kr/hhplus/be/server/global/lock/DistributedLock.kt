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
        // 락 소유권 식별을 위한 고유 값 생성
        val lockValue = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < waitTimeoutMs) {
            if (tryAcquireLock(lockKey, lockValue, lockTimeoutMs)) {
                return try {
                    action()
                } finally {
                    // 락 소유권 확인 후 안전하게 해제
                    releaseLock(lockKey, lockValue)
                }
            }

            Thread.sleep(50)
        }

        throw ConcurrentAccessException("락 획득에 실패했습니다: $lockKey")
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
}