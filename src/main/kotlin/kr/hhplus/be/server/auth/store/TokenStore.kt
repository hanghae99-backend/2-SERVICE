package kr.hhplus.be.server.auth.store

import kr.hhplus.be.server.auth.WaitingToken
import kr.hhplus.be.server.auth.TokenStatus

interface TokenStore {
    // 기본 CRUD
    fun save(token: WaitingToken)
    fun findByToken(token: String): WaitingToken?
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
    
    // 콘서트 예약 특화
    fun findExpiredActiveTokens(): List<String>
}
