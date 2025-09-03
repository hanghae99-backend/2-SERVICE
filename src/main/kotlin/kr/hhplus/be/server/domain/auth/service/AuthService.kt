package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.kafka.TokenQueueProducer
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AuthService(
    private val tokenQueueProducer: TokenQueueProducer,
    private val tokenFactory: TokenFactory
) {
    
    @ValidateUserId
    fun issueToken(userId: Long): TokenIssueDetail {
        val waitingToken = tokenFactory.createWaitingToken(userId)
        tokenQueueProducer.sendTokenToQueue(waitingToken)
        
        return TokenIssueDetail.fromTokenWithDetails(
            token = waitingToken.token,
            status = "WAITING",
            message = "대기열에 등록되었습니다",
            userId = userId,
            queuePosition = null,
            estimatedWaitingTime = null,
            issuedAt = LocalDateTime.now()
        )
    }
    
    fun getTokenStatusDetail(token: String): TokenQueueDetail {
        return TokenQueueDetail.fromTokenWithQueue(
            token = token,
            status = "WAITING",
            message = "대기 중입니다",
            queuePosition = null,
            estimatedWaitingTime = null
        )
    }
}