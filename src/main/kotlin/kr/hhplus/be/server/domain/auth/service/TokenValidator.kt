package kr.hhplus.be.server.domain.auth.service

import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import org.springframework.stereotype.Component

@Component
class TokenValidator(
    private val tokenManager: TokenManager
) {
    
    fun validateTokenExists(token: String): WaitingToken {
        return tokenManager.findToken(token) ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
    }
    
    fun validateActiveToken(token: String): WaitingToken {
        val waitingToken = validateTokenExists(token)
        val status = tokenManager.getTokenStatus(token)
        
        if (status != TokenStatus.ACTIVE) {
            throw TokenActivationException("활성화된 토큰이 아닙니다. 현재 상태: $status")
        }
        
        return waitingToken
    }
    
    fun validateTokenActivation(currentStatus: TokenStatus) {
        if (currentStatus != TokenStatus.WAITING) {
            throw TokenActivationException("대기 중인 토큰만 활성화 가능합니다. 현재 상태: $currentStatus")
        }
    }
}