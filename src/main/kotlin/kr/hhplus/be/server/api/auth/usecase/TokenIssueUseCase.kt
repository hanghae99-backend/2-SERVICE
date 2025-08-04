package kr.hhplus.be.server.api.auth.usecase

import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TokenIssueUseCase(
    private val tokenDomainService: TokenDomainService,
    private val tokenFactory: TokenFactory,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val queueManager: QueueManager
) {
    
    @ValidateUserId
    fun execute(userId: Long): TokenIssueDetail {
        val waitingToken = tokenFactory.createWaitingToken(userId)

        tokenLifecycleManager.saveToken(waitingToken)
        queueManager.addToQueue(waitingToken.token)

        val queuePosition = queueManager.getQueuePosition(waitingToken.token) + 1
        val estimatedWaitingTime = tokenDomainService.calculateWaitingTime(queuePosition)
        
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
}
