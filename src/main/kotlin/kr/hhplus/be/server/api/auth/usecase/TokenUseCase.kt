package kr.hhplus.be.server.api.auth.usecase

import kr.hhplus.be.server.api.auth.dto.TokenDto
import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDateTime


@Service
class TokenUseCase(
    private val tokenDomainService: TokenDomainService,
    private val userService: UserService,
    private val tokenFactory: TokenFactory,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val queueManager: QueueManager
) {
    

    fun issueWaitingToken(userId: Long): TokenIssueDetail {

        val user = userService.getUserById(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        

        val waitingToken = tokenFactory.createWaitingToken(userId)
        

        tokenLifecycleManager.saveToken(waitingToken)
        

        queueManager.addToQueue(waitingToken.token)
        

        val queuePosition = queueManager.getQueuePosition(waitingToken.token) + 1 // 1부터 시작
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
    

    fun getTokenQueueStatus(token: String): TokenQueueDetail {

        tokenLifecycleManager.findToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        

        val status = tokenLifecycleManager.getTokenStatus(token)
        

        val (message, queuePosition, estimatedTime) = when (status) {
            TokenStatus.WAITING -> {
                val position = queueManager.getQueuePosition(token)
                val pos = if (position >= 0) position + 1 else null
                val time = pos?.let { tokenDomainService.calculateWaitingTime(it) }
                Triple(tokenDomainService.getStatusMessage(status), pos, time)
            }
            TokenStatus.ACTIVE -> Triple(tokenDomainService.getStatusMessage(status), null, null)
            TokenStatus.EXPIRED -> Triple(tokenDomainService.getStatusMessage(status), null, null)
        }
        
        return TokenQueueDetail.fromTokenWithQueue(
            token = token,
            status = status.name,
            message = message,
            queuePosition = queuePosition,
            estimatedWaitingTime = estimatedTime
        )
    }
    

    fun getSimpleTokenStatus(token: String): TokenDto {

        tokenLifecycleManager.findToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        

        val status = tokenLifecycleManager.getTokenStatus(token)
        

        val message = tokenDomainService.getStatusMessage(status)
        
        return TokenDto.create(
            token = token,
            status = status.name,
            message = message
        )
    }
    

    fun validateActiveToken(token: String): WaitingToken {

        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        

        tokenDomainService.validateActiveToken(waitingToken, status)
        
        return waitingToken!!
    }
    

    fun completeReservation(token: String) {

        tokenLifecycleManager.findToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")

        tokenLifecycleManager.completeToken(token)
    }
    

    fun processQueueAutomatically() {

        tokenLifecycleManager.cleanupExpiredTokens()
        

        queueManager.processQueueAutomatically()
    }
    

    fun cleanupExpiredActiveTokens() {
        tokenLifecycleManager.cleanupExpiredTokens()
    }
}
