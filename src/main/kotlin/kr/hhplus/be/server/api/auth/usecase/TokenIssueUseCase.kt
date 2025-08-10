package kr.hhplus.be.server.api.auth.usecase

import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Service
class TokenIssueUseCase(
    private val tokenDomainService: TokenDomainService,
    private val tokenFactory: TokenFactory,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val queueManager: QueueManager
) {
    
    // 사용자별 락을 관리하는 맵
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()
    
    @ValidateUserId
    fun execute(userId: Long): TokenIssueDetail {
        // 사용자별 락 획득
        val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }
        
        lock.lock()
        try {
            return executeInternal(userId)
        } finally {
            lock.unlock()
        }
    }
    
    private fun executeInternal(userId: Long): TokenIssueDetail {
        // 동일한 사용자가 이미 유효한 토큰을 가지고 있는지 확인
        val existingToken = tokenLifecycleManager.findActiveTokenByUserId(userId)
        if (existingToken != null) {
            val queuePosition = queueManager.getQueuePosition(existingToken.token) + 1
            val estimatedWaitingTime = tokenDomainService.calculateWaitingTime(queuePosition)
            
            return TokenIssueDetail.fromTokenWithDetails(
                token = existingToken.token,
                status = "WAITING",
                message = "이미 대기열에 등록된 토큰입니다",
                userId = userId,
                queuePosition = queuePosition,
                estimatedWaitingTime = estimatedWaitingTime
            )
        }

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
