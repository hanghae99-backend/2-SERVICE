package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class TokenManager(
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val userService: UserService,
    private val queueService: QueueService
) {
    
    fun findActiveTokenByUserId(userId: Long): WaitingToken? {
        return tokenStore.findActiveTokenByUserId(userId)
    }
    
    fun createNewToken(userId: Long): TokenIssueDetail {
        val user = userService.getUserById(userId) ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        
        val waitingToken = tokenFactory.createWaitingToken(userId)
        tokenStore.save(waitingToken)
        queueService.addToQueue(waitingToken.token)
        
        val queuePosition = queueService.getQueuePosition(waitingToken.token) + 1
        val estimatedWaitingTime = queueService.calculateWaitingTime(queuePosition)
        
        return TokenIssueDetail.fromTokenWithDetails(
            token = waitingToken.token,
            status = "WAITING",
            message = "대기열에 등록되었습니다",
            userId = userId,
            queuePosition = queuePosition,
            estimatedWaitingTime = estimatedWaitingTime,
            issuedAt = LocalDateTime.now()
        )
    }
    
    fun findToken(token: String): WaitingToken? {
        return tokenStore.findByToken(token)
    }
    
    fun getTokenStatus(token: String) = tokenStore.getTokenStatus(token)
    
    fun expireToken(token: String) = tokenStore.expireToken(token)
    
    fun activateToken(token: String) = tokenStore.activateToken(token)
    
    fun completeToken(token: String) = tokenStore.expireToken(token)
    
    fun cleanupExpiredTokens() = tokenStore.removeExpiredTokens()
}