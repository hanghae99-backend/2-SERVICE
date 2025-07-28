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

    companion object {
        private const val TOKEN_PREFIX = "waiting_token:"
        private const val USER_PREFIX = "user_tokens:"
        private const val WAITING_QUEUE = "waiting_queue"
        private const val ACTIVE_TOKENS = "active_tokens"
        private const val ACTIVE_TOKEN_TIMESTAMP_PREFIX = "active_timestamp:"
        private val TOKEN_TTL = Duration.ofMinutes(30)
        private val ACTIVE_TTL = Duration.ofMinutes(10) // 10분 활성 시간
    }

    override fun save(token: WaitingToken) {
        val key = TOKEN_PREFIX + token.token
        val value = objectMapper.writeValueAsString(token)

        // 토큰 정보 저장
        redisTemplate.opsForValue().set(key, value, TOKEN_TTL)
        redisTemplate.opsForSet().add(USER_PREFIX + token.userId, token.token)
    }

    override fun findByToken(token: String): WaitingToken? {
        val value = redisTemplate.opsForValue().get(TOKEN_PREFIX + token) ?: return null
        return objectMapper.readValue(value, WaitingToken::class.java)
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

    // ===== 상태 관리 (Redis 기반) =====

    override fun getTokenStatus(token: String): TokenStatus {
        return when {
            redisTemplate.opsForSet().isMember(ACTIVE_TOKENS, token) == true -> TokenStatus.ACTIVE
            redisTemplate.opsForList().indexOf(WAITING_QUEUE, token) != null -> TokenStatus.WAITING
            else -> TokenStatus.EXPIRED
        }
    }

    override fun activateToken(token: String) {
        // 대기열에서 제거하고 활성 토큰으로 이동
        removeFromWaitingQueue(token)
        redisTemplate.opsForSet().add(ACTIVE_TOKENS, token)

        // 활성화 시간 기록 (만료 체크용)
        redisTemplate.opsForValue().set(
            ACTIVE_TOKEN_TIMESTAMP_PREFIX + token,
            System.currentTimeMillis().toString(),
            ACTIVE_TTL
        )

        redisTemplate.expire(ACTIVE_TOKENS, ACTIVE_TTL)
    }

    override fun expireToken(token: String) {
        // 모든 곳에서 제거
        removeFromWaitingQueue(token)
        redisTemplate.opsForSet().remove(ACTIVE_TOKENS, token)
        redisTemplate.delete(ACTIVE_TOKEN_TIMESTAMP_PREFIX + token)
    }

    override fun countActiveTokens(): Long {
        return redisTemplate.opsForSet().size(ACTIVE_TOKENS) ?: 0L
    }

    // ===== Queue 관리 (간소화) =====

    override fun addToWaitingQueue(token: String) {
        redisTemplate.opsForList().rightPush(WAITING_QUEUE, token)
    }

    private fun removeFromWaitingQueue(token: String) {
        redisTemplate.opsForList().remove(WAITING_QUEUE, 0, token)
    }

    override fun getNextTokensFromQueue(count: Int): List<String> {
        val tokens = mutableListOf<String>()
        repeat(count) {
            val token = redisTemplate.opsForList().leftPop(WAITING_QUEUE)
            if (token != null) {
                tokens.add(token)
            }
        }
        return tokens
    }

    override fun getQueueSize(): Long {
        return redisTemplate.opsForList().size(WAITING_QUEUE) ?: 0L
    }

    override fun getQueuePosition(token: String): Int {
        // Redis의 LPOS 명령어를 사용하여 대기열에서 토큰의 위치를 찾음
        val position = redisTemplate.opsForList().indexOf(WAITING_QUEUE, token)
        return position?.toInt() ?: -1 // 찾지 못했으면 -1 반환
    }

    // ===== 콘서트 예약 서비스 특화 =====

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
                // 타임스탬프가 없는 토큰은 만료된 것으로 간주
                expiredTokens.add(token)
            }
        }

        return expiredTokens
    }
}