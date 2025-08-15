package kr.hhplus.be.server.domain.auth.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisTokenStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : TokenStore {
    fun flushAll() {
        redisTemplate.connectionFactory?.connection?.flushAll()
    }

    companion object {
        private const val TOKEN_PREFIX = "waiting_token:"
        private const val USER_PREFIX = "user_tokens:"
        private const val WAITING_QUEUE_ZSET = "waiting_queue_zset"
        private const val ACTIVE_TOKENS = "active_tokens"
        private const val ACTIVE_TOKEN_TIMESTAMP_PREFIX = "active_timestamp:"
        private const val QUEUE_SEQUENCE_KEY = "queue_sequence"
        private val TOKEN_TTL = Duration.ofMinutes(30)
        private val ACTIVE_TTL = Duration.ofMinutes(10)
    }

    override fun save(token: WaitingToken) {
        val key = TOKEN_PREFIX + token.token
        val value = objectMapper.writeValueAsString(token)

        redisTemplate.opsForValue().set(key, value, TOKEN_TTL)
        redisTemplate.opsForSet().add(USER_PREFIX + token.userId, token.token)
    }

    override fun findByToken(token: String): WaitingToken? {
        val value = redisTemplate.opsForValue().get(TOKEN_PREFIX + token) ?: return null
        return objectMapper.readValue(value, WaitingToken::class.java)
    }
    
    override fun findActiveTokenByUserId(userId: Long): WaitingToken? {
        val userTokens = redisTemplate.opsForSet().members(USER_PREFIX + userId) ?: return null
        
        for (tokenStr in userTokens) {
            if (redisTemplate.opsForSet().isMember(ACTIVE_TOKENS, tokenStr) == true) {
                return findByToken(tokenStr)
            }
        }
        return null
    }

    override fun delete(token: String) {
        val waitingToken = findByToken(token)
        redisTemplate.delete(TOKEN_PREFIX + token)
        waitingToken?.let {
            redisTemplate.opsForSet().remove(USER_PREFIX + it.userId, token)
            removeFromWaitingQueue(token)
            redisTemplate.opsForSet().remove(ACTIVE_TOKENS, token)
            redisTemplate.delete(ACTIVE_TOKEN_TIMESTAMP_PREFIX + token)
        }
    }

    override fun validate(token: String): Boolean = redisTemplate.hasKey(TOKEN_PREFIX + token)

    // 상태 관리

    override fun getTokenStatus(token: String): TokenStatus {
        return when {
            redisTemplate.opsForSet().isMember(ACTIVE_TOKENS, token) == true -> TokenStatus.ACTIVE
            redisTemplate.opsForZSet().rank(WAITING_QUEUE_ZSET, token) != null -> TokenStatus.WAITING
            else -> TokenStatus.EXPIRED
        }
    }

    override fun activateToken(token: String) {
        removeFromWaitingQueue(token)
        redisTemplate.opsForSet().add(ACTIVE_TOKENS, token)

        redisTemplate.opsForValue().set(
            ACTIVE_TOKEN_TIMESTAMP_PREFIX + token,
            System.currentTimeMillis().toString(),
            ACTIVE_TTL
        )
    }

    override fun expireToken(token: String) {
        removeFromWaitingQueue(token)
        redisTemplate.opsForSet().remove(ACTIVE_TOKENS, token)
        redisTemplate.delete(ACTIVE_TOKEN_TIMESTAMP_PREFIX + token)
    }

    override fun countActiveTokens(): Long {
        return redisTemplate.opsForSet().size(ACTIVE_TOKENS) ?: 0L
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

        return tokens?.toList() ?: emptyList()
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
        return redisTemplate.opsForSet().isMember(ACTIVE_TOKENS, token) == true
    }

    // 콘서트 예약 특화

    override fun findExpiredActiveTokens(): List<String> {
        val activeTokens = redisTemplate.opsForSet().members(ACTIVE_TOKENS) ?: return emptyList()
        val expiredTokens = mutableListOf<String>()
        val currentTime = System.currentTimeMillis()
        val ttlMillis = ACTIVE_TTL.toMillis()

        activeTokens.forEach { token ->
            val timestampStr = redisTemplate.opsForValue().get(ACTIVE_TOKEN_TIMESTAMP_PREFIX + token)
            if (timestampStr != null) {
                val activatedTime = timestampStr.toLongOrNull() ?: 0L
                if (currentTime - activatedTime > ttlMillis) {
                    expiredTokens.add(token)
                }
            } else {
                expiredTokens.add(token)
            }
        }

        return expiredTokens
    }
}