package kr.hhplus.be.server.api.auth.usecase

import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.auth.service.QueueManager
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TokenQueueStatusUseCase(
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val queueManager: QueueManager
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(TokenQueueStatusUseCase::class.java)
    }
    
    fun execute(token: String): TokenQueueDetail {
        logger.debug("토큰 상태 조회 시작 - token: {}", token)
        
        val tokenEntity = tokenLifecycleManager.findToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다: $token")

        val status = tokenLifecycleManager.getTokenStatus(token)
        
        logger.debug("토큰 상태 확인 완료 - token: {}, status: {}", token, status)

        val (message, queuePosition, estimatedTime) = when (status) {
            TokenStatus.WAITING -> {
                logger.debug("대기 중인 토큰 처리 - token: {}", token)
                val position = queueManager.getQueuePosition(token)
                val pos = if (position >= 0) position + 1 else null
                val time = pos?.let { tokenDomainService.calculateWaitingTime(it) }
                
                logger.debug("대기열 정보 - token: {}, position: {}, estimatedTime: {}", token, pos, time)
                Triple(tokenDomainService.getStatusMessage(status), pos, time)
            }
            TokenStatus.ACTIVE -> {
                logger.debug("활성 상태 토큰 - token: {}", token)
                Triple(tokenDomainService.getStatusMessage(status), null, null)
            }
            TokenStatus.EXPIRED -> {
                logger.debug("만료된 토큰 - token: {}", token)
                Triple(tokenDomainService.getStatusMessage(status), null, null)
            }
            TokenStatus.USED -> {
                logger.debug("사용된 토큰 - token: {}", token)
                Triple(tokenDomainService.getStatusMessage(status), null, null)
            }
        }

        val result = TokenQueueDetail.fromTokenWithQueue(
            token = token,
            status = status.name,
            message = message,
            queuePosition = queuePosition,
            estimatedWaitingTime = estimatedTime
        )
        
        logger.debug("토큰 상태 조회 완료 - token: {}, result: {}", token, result)
        
        return result
    }
}
