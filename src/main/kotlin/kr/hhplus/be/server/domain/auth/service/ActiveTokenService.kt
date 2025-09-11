package kr.hhplus.be.server.domain.auth.service

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ActiveTokenService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    
    companion object {
        private const val ACTIVE_TOKENS_HASH = "active_tokens_hash"
        private val ACTIVE_TTL = Duration.ofMinutes(10)
    }

    fun activateToken(token: String) {
        val expireTime = System.currentTimeMillis() + ACTIVE_TTL.toMillis()
        redisTemplate.opsForHash<String, String>().put(ACTIVE_TOKENS_HASH, token, expireTime.toString())
    }

    fun isTokenActive(token: String): Boolean {
        return redisTemplate.opsForHash<String, String>().hasKey(ACTIVE_TOKENS_HASH, token)
    }

    fun completeToken(token: String) {
        redisTemplate.opsForHash<String, String>().delete(ACTIVE_TOKENS_HASH, token)
    }

    fun removeExpiredTokens() {
        val activeTokens = redisTemplate.opsForHash<String, String>().entries(ACTIVE_TOKENS_HASH)
        val currentTime = System.currentTimeMillis()

        activeTokens.filter { (_, expireTimeStr) ->
            val expireTime = expireTimeStr.toLongOrNull() ?: 0L
            currentTime >= expireTime
        }.forEach { (token, _) ->
            redisTemplate.opsForHash<String, String>().delete(ACTIVE_TOKENS_HASH, token)
        }
    }
}