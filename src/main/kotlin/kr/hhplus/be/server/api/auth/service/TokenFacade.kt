package kr.hhplus.be.server.api.auth.service

import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.service.QueueService
import kr.hhplus.be.server.domain.auth.service.TokenManager
import kr.hhplus.be.server.domain.auth.service.TokenValidator
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.springframework.stereotype.Service

@Service
class TokenFacade(
    private val tokenManager: TokenManager,
    private val queueService: QueueService,
    private val tokenValidator: TokenValidator
) {
    
    @ValidateUserId
    fun issueToken(userId: Long): TokenIssueDetail {
        val existingToken = tokenManager.findActiveTokenByUserId(userId)
        if (existingToken != null) {
            return queueService.getTokenQueueInfo(existingToken.token, userId)
        }
        
        return tokenManager.createNewToken(userId)
    }
    
    fun getTokenStatus(token: String): TokenQueueDetail {
        tokenValidator.validateTokenExists(token)
        return queueService.getTokenStatusDetail(token)
    }
}