package kr.hhplus.be.server.config

import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.global.client.BalanceApiClient
import kr.hhplus.be.server.global.client.ReservationApiClient
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import kr.hhplus.be.server.global.client.SeatApiClient
import kr.hhplus.be.server.config.mock.MockBalanceApiClient
import kr.hhplus.be.server.config.mock.MockReservationApiClient
import kr.hhplus.be.server.config.mock.MockConcertDataPlatformClient
import kr.hhplus.be.server.config.mock.MockSeatApiClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Mock 기반 테스트용 설정
 * Redis 없이 메모리 기반으로 TokenStore 동작을 시뮬레이션
 */
@TestConfiguration
class MockTestConfiguration {
    
    @Bean
    @Primary
    fun mockTokenStore(): TokenStore {
        return InMemoryTokenStore()
    }
    
    @Bean
    @Primary
    fun mockBalanceApiClient(): BalanceApiClient {
        return MockBalanceApiClient()
    }
    
    @Bean
    @Primary
    fun mockReservationApiClient(): ReservationApiClient {
        return MockReservationApiClient()
    }
    
    @Bean
    @Primary
    fun mockConcertDataPlatformClient(): ConcertDataPlatformClient {
        return MockConcertDataPlatformClient()
    }
    
    @Bean
    @Primary
    fun mockSeatApiClient(): SeatApiClient {
        return MockSeatApiClient()
    }
}

/**
 * 메모리 기반 TokenStore 구현체
 * 테스트용으로 Redis 동작을 시뮬레이션
 */
class InMemoryTokenStore : TokenStore {
    
    private val tokens = ConcurrentHashMap<String, WaitingToken>()
    private val tokenStatus = ConcurrentHashMap<String, TokenStatus>()
    private val waitingQueue = ConcurrentLinkedQueue<String>()
    private val activeTokens = ConcurrentHashMap<String, Long>()
    private val queuePositions = ConcurrentHashMap<String, Int>()
    private val userActiveTokens = ConcurrentHashMap<Long, String>()
    
    // 기본 CRUD
    override fun save(token: WaitingToken) {
        tokens[token.token] = token
        tokenStatus[token.token] = TokenStatus.WAITING
    }
    
    override fun findByToken(token: String): WaitingToken? {
        return tokens[token]
    }
    
    override fun findActiveTokenByUserId(userId: Long): WaitingToken? {
        val tokenString = userActiveTokens[userId] ?: return null
        return tokens[tokenString]
    }
    
    override fun findAll(): List<WaitingToken> {
        return tokens.values.toList()
    }
    
    override fun delete(token: String) {
        val waitingToken = tokens[token]
        tokens.remove(token)
        tokenStatus.remove(token)
        waitingQueue.remove(token)
        activeTokens.remove(token)
        queuePositions.remove(token)
        waitingToken?.let { userActiveTokens.remove(it.userId) }
        updateQueuePositions()
    }
    
    override fun validate(token: String): Boolean {
        return tokens.containsKey(token)
    }
    
    // 상태 관리
    override fun getTokenStatus(token: String): TokenStatus {
        return tokenStatus[token] ?: TokenStatus.EXPIRED
    }
    
    override fun activateToken(token: String) {
        val waitingToken = tokens[token] ?: return
        tokenStatus[token] = TokenStatus.ACTIVE
        activeTokens[token] = System.currentTimeMillis()
        userActiveTokens[waitingToken.userId] = token
        waitingQueue.remove(token)
        queuePositions.remove(token)
        updateQueuePositions()
    }
    
    override fun expireToken(token: String) {
        val waitingToken = tokens[token]
        tokenStatus[token] = TokenStatus.EXPIRED
        activeTokens.remove(token)
        waitingQueue.remove(token)
        queuePositions.remove(token)
        waitingToken?.let { userActiveTokens.remove(it.userId) }
        updateQueuePositions()
    }
    
    override fun countActiveTokens(): Long {
        return activeTokens.size.toLong()
    }
    
    // Queue 관리
    override fun addToWaitingQueue(token: String) {
        tokenStatus[token] = TokenStatus.WAITING
        waitingQueue.offer(token)
        updateQueuePositions()
    }
    
    override fun getNextTokensFromQueue(count: Int): List<String> {
        val tokens = mutableListOf<String>()
        repeat(count) {
            val token = waitingQueue.poll()
            if (token != null) {
                tokens.add(token)
            }
        }
        updateQueuePositions()
        return tokens
    }
    
    override fun getQueueSize(): Long {
        return waitingQueue.size.toLong()
    }
    
    override fun getQueuePosition(token: String): Int {
        return queuePositions[token] ?: -1
    }
    
    // 편의 메서드
    override fun getTokenStatusAndPosition(token: String): Pair<TokenStatus, Int?> {
        val status = getTokenStatus(token)
        val position = if (status == TokenStatus.WAITING) getQueuePosition(token) else null
        return status to position
    }
    
    override fun isTokenInQueue(token: String): Boolean {
        return waitingQueue.contains(token)
    }
    
    override fun isTokenActive(token: String): Boolean {
        return tokenStatus[token] == TokenStatus.ACTIVE
    }
    
    // 콘서트 예약 특화
    override fun findExpiredActiveTokens(): List<String> {
        val currentTime = System.currentTimeMillis()
        val expiredTokens = mutableListOf<String>()
        
        activeTokens.forEach { (token, activatedTime) ->
            // 10분(600000ms) 후 만료
            if (currentTime - activatedTime > 600000) {
                expiredTokens.add(token)
            }
        }
        
        return expiredTokens
    }
    
    override fun removeExpiredTokens() {
        val expiredTokens = findExpiredActiveTokens()
        expiredTokens.forEach { token ->
            expireToken(token)
        }
    }
    
    private fun updateQueuePositions() {
        queuePositions.clear()
        waitingQueue.forEachIndexed { index, token ->
            queuePositions[token] = index + 1
        }
    }
    
    // 테스트 헬퍼 메서드들
    fun clear() {
        tokens.clear()
        tokenStatus.clear()
        waitingQueue.clear()
        activeTokens.clear()
        queuePositions.clear()
        userActiveTokens.clear()
    }
    
    fun getWaitingQueueSize(): Int = waitingQueue.size
    fun getActiveTokensCount(): Int = activeTokens.size
    fun getAllTokens(): Map<String, WaitingToken> = tokens.toMap()
}

