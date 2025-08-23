package kr.hhplus.be.server.domain.auth.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisTokenStore(
    private val redisTemplate: RedisTemplate<String, Any>,
    @Qualifier("redis") private val objectMapper: ObjectMapper
) : TokenStore {
    fun flushAll() {
        redisTemplate.connectionFactory?.connection?.use { connection ->
            connection.serverCommands().flushAll()
        }
    }

    companion object {
        private const val TOKEN_PREFIX = "waiting_token:"
        private const val USER_PREFIX = "user_tokens:"
        private const val WAITING_QUEUE_ZSET = "waiting_queue_zset"
        private const val ACTIVE_TOKENS_HASH = "active_tokens_hash"  // Hash로 변경 (key: uuid, value: expireTime)
        private const val QUEUE_SEQUENCE_KEY = "queue_sequence"
        private val TOKEN_TTL = Duration.ofMinutes(30)
        private val ACTIVE_TTL = Duration.ofMinutes(10)
    }

    override fun save(token: WaitingToken) {
        val key = TOKEN_PREFIX + token.token
        redisTemplate.opsForValue().set(key, token, TOKEN_TTL)
        redisTemplate.opsForSet().add(USER_PREFIX + token.userId, token.token)
    }

    override fun findByToken(token: String): WaitingToken? {
        val value = redisTemplate.opsForValue().get(TOKEN_PREFIX + token) ?: return null
        return value as? WaitingToken
    }
    
    override fun findActiveTokenByUserId(userId: Long): WaitingToken? {
        val userTokens = redisTemplate.opsForSet().members(USER_PREFIX + userId) ?: return null
        
        for (tokenAny in userTokens) {
            val tokenStr = tokenAny.toString()
            if (redisTemplate.opsForHash<String, String>().hasKey(ACTIVE_TOKENS_HASH, tokenStr)) {
                return findByToken(tokenStr)
            }
        }
        return null
    }
    
    override fun findAll(): List<WaitingToken> {
        val keys = redisTemplate.keys("$TOKEN_PREFIX*")
        val tokens = mutableListOf<WaitingToken>()
        
        keys?.forEach { key ->
            val value = redisTemplate.opsForValue().get(key)
            if (value != null) {
                try {
                    val token = value as? WaitingToken
                    if (token != null) {
                        tokens.add(token)
                    }
                } catch (e: Exception) {
                    // 파싱 실패 시 무시
                }
            }
        }
        
        return tokens
    }

    override fun delete(token: String) {
        val waitingToken = findByToken(token)
        redisTemplate.delete(TOKEN_PREFIX + token)
        waitingToken?.let {
            redisTemplate.opsForSet().remove(USER_PREFIX + it.userId, token)
            removeFromWaitingQueue(token)
            redisTemplate.opsForHash<String, String>().delete(ACTIVE_TOKENS_HASH, token)
        }
    }

    override fun validate(token: String): Boolean = redisTemplate.hasKey(TOKEN_PREFIX + token)

    // 상태 관리

    override fun getTokenStatus(token: String): TokenStatus {
        return when {
            redisTemplate.opsForHash<String, String>().hasKey(ACTIVE_TOKENS_HASH, token) -> TokenStatus.ACTIVE
            redisTemplate.opsForZSet().rank(WAITING_QUEUE_ZSET, token) != null -> TokenStatus.WAITING
            else -> TokenStatus.EXPIRED
        }
    }

    override fun activateToken(token: String) {
        removeFromWaitingQueue(token)
        val expireTime = System.currentTimeMillis() + ACTIVE_TTL.toMillis()
        redisTemplate.opsForHash<String, String>().put(ACTIVE_TOKENS_HASH, token, expireTime.toString())
    }

    override fun expireToken(token: String) {
        removeFromWaitingQueue(token)
        redisTemplate.opsForHash<String, String>().delete(ACTIVE_TOKENS_HASH, token)
    }

    override fun countActiveTokens(): Long {
        return redisTemplate.opsForHash<String, String>().size(ACTIVE_TOKENS_HASH)
    }

    // Queue 관리

    override fun addToWaitingQueue(token: String) {
        val sequence = redisTemplate.opsForValue().increment(QUEUE_SEQUENCE_KEY) ?: 1L
        redisTemplate.opsForZSet().add(WAITING_QUEUE_ZSET, token, sequence.toDouble())
    }

    private fun removeFromWaitingQueue(token: String) {
        redisTemplate.opsForZSet().remove(WAITING_QUEUE_ZSET, token)
    }

    override fun getNextTokensFromQueue(count: Int): List<String> {
        val tokens = redisTemplate.opsForZSet().range(WAITING_QUEUE_ZSET, 0, (count - 1).toLong())

        tokens?.forEach { token ->
            redisTemplate.opsForZSet().remove(WAITING_QUEUE_ZSET, token)
        }

        return tokens?.map { it.toString() } ?: emptyList()
    }

    override fun getQueueSize(): Long {
        return redisTemplate.opsForZSet().zCard(WAITING_QUEUE_ZSET) ?: 0L
    }

    override fun getQueuePosition(token: String): Int {
        val rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE_ZSET, token)
        return rank?.toInt() ?: -1
    }

    // 편의 메서드

    override fun getTokenStatusAndPosition(token: String): Pair<TokenStatus, Int?> {
        val status = getTokenStatus(token)
        val position = if (status == TokenStatus.WAITING) {
            getQueuePosition(token).takeIf { it >= 0 }
        } else {
            null
        }
        return Pair(status, position)
    }

    override fun isTokenInQueue(token: String): Boolean {
        return redisTemplate.opsForZSet().rank(WAITING_QUEUE_ZSET, token) != null
    }

    override fun isTokenActive(token: String): Boolean {
        return redisTemplate.opsForHash<String, String>().hasKey(ACTIVE_TOKENS_HASH, token)
    }

    // 콘서트 예약 특화

    override fun findExpiredActiveTokens(): List<String> {
        val activeTokens = redisTemplate.opsForHash<String, String>().entries(ACTIVE_TOKENS_HASH)
        val expiredTokens = mutableListOf<String>()
        val currentTime = System.currentTimeMillis()

        activeTokens.forEach { (token, expireTimeStr) ->
            val expireTime = expireTimeStr.toLongOrNull() ?: 0L
            if (currentTime >= expireTime) {
                expiredTokens.add(token)
            }
        }

        return expiredTokens
    }
}
