package kr.hhplus.be.server.domain.auth.repositories

import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken

interface TokenStore {
    // 기본 CRUD
    fun save(token: WaitingToken)
    fun findByToken(token: String): WaitingToken?
    fun findActiveTokenByUserId(userId: Long): WaitingToken?
    fun delete(token: String)
    fun validate(token: String): Boolean

    // 상태 관리 (Redis 기반)
    fun getTokenStatus(token: String): TokenStatus
    fun activateToken(token: String)
    fun expireToken(token: String)
    fun countActiveTokens(): Long

    // Queue 관리
    fun addToWaitingQueue(token: String)
    fun getNextTokensFromQueue(count: Int): List<String>
    fun getQueueSize(): Long
    fun getQueuePosition(token: String): Int

    // 편의 메서드
    fun getTokenStatusAndPosition(token: String): Pair<TokenStatus, Int?>
    fun isTokenInQueue(token: String): Boolean
    fun isTokenActive(token: String): Boolean

    // 콘서트 예약 특화
    fun findExpiredActiveTokens(): List<String>
}