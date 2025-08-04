package kr.hhplus.be.server.api.auth.usecase

import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import org.springframework.stereotype.Service

@Service
class TokenQueueStatusUseCase(
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val queueManager: QueueManager
) {
    
    fun execute(token: String): TokenQueueDetail {
        tokenLifecycleManager.findToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")

        val status = tokenLifecycleManager.getTokenStatus(token)

        val (message, queuePosition, estimatedTime) = when (status) {
            TokenStatus.WAITING -> {
                // 큐 위치 및 예상 대기시간 계산
                val position = queueManager.getQueuePosition(token)
                val pos = if (position >= 0) position + 1 else null
                val time = pos?.let { tokenDomainService.calculateWaitingTime(it) }
                Triple(tokenDomainService.getStatusMessage(status), pos, time)
            }
            TokenStatus.ACTIVE -> {
                Triple(tokenDomainService.getStatusMessage(status), null, null)
            }
            TokenStatus.EXPIRED -> {
                Triple(tokenDomainService.getStatusMessage(status), null, null)
            }
        }

        return TokenQueueDetail.fromTokenWithQueue(
            token = token,
            status = status.name,
            message = message,
            queuePosition = queuePosition,
            estimatedWaitingTime = estimatedTime
        )
    }
}
