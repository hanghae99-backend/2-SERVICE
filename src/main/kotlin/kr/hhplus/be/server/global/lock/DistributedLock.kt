package kr.hhplus.be.server.global.lock

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

@Component
class DistributedLock(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(DistributedLock::class.java)
    
    /**
     * 분산 락을 획득하고 작업을 실행합니다.
     * 
     * @param lockKey 락 키
     * @param lockTimeoutMs 락 타임아웃 (밀리초)
     * @param waitTimeoutMs 대기 타임아웃 (밀리초)
     * @param action 실행할 작업
     * @return 작업 결과
     */
    fun <T> executeWithLock(
        lockKey: String,
        lockTimeoutMs: Long = 10000L,
        waitTimeoutMs: Long = 5000L,
        action: () -> T
    ): T {
        val lockValue = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        
        // 락 획득 시도
        while (System.currentTimeMillis() - startTime < waitTimeoutMs) {
            if (tryAcquireLock(lockKey, lockValue, lockTimeoutMs)) {
                logger.debug("락 획득 성공: $lockKey")
                
                return try {
                    action()
                } finally {
                    releaseLock(lockKey, lockValue)
                    logger.debug("락 해제 완료: $lockKey")
                }
            }
            
            // 잠시 대기 후 재시도
            Thread.sleep(50)
        }
        
        throw ConcurrentAccessException("락 획득에 실패했습니다: $lockKey")
    }
    
    /**
     * 락 획득을 시도합니다.
     */
    private fun tryAcquireLock(key: String, value: String, timeoutMs: Long): Boolean {
        val result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofMilliseconds(timeoutMs))
        return result ?: false
    }
    
    /**
     * 락을 해제합니다.
     * Lua 스크립트를 사용하여 원자적으로 처리합니다.
     */
    private fun releaseLock(key: String, value: String) {
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
            logger.warn("락 해제 중 오류 발생: $key", e)
        }
    }
}