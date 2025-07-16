package kr.hhplus.be.server.auth

import kr.hhplus.be.server.user.UserService
import kr.hhplus.be.server.user.UserNotFoundException
import kr.hhplus.be.server.auth.store.TokenStore
import org.springframework.stereotype.Service
import java.util.*

@Service
class TokenService(
    private val userService: UserService,
    private val tokenStore: TokenStore
) {
    
    companion object {
        private const val MAX_ACTIVE_TOKENS = 100L
    }
    
    /**
     * 콘서트 예약 토큰 발급
     */
    fun issueWaitingToken(userId: Long): WaitingToken {
        // 사용자 존재 여부 확인
        userService.findUserById(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        
        val token = generateToken()
        val waitingToken = WaitingToken(token = token, userId = userId)
        
        // 토큰 저장 및 대기열에 추가
        tokenStore.save(waitingToken)
        tokenStore.addToWaitingQueue(token)
        
        return waitingToken
    }
    
    /**
     * 토큰 상태 확인 (단순 상태만)
     */
    fun getTokenStatus(token: String): TokenStatusResponse {
        val waitingToken = tokenStore.findByToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        
        val status = tokenStore.getTokenStatus(token)
        val message = when (status) {
            TokenStatus.WAITING -> "대기 중입니다"
            TokenStatus.ACTIVE -> "예약 가능합니다"
            TokenStatus.EXPIRED -> "토큰이 만료되었습니다"
        }
        
        return TokenStatusResponse(status, message)
    }
    
    /**
     * 모든 API에서 사용 - 토큰 검증
     */
    fun validateActiveToken(token: String): WaitingToken {
        val waitingToken = tokenStore.findByToken(token)
            ?: throw TokenNotFoundException("유효하지 않은 토큰입니다.")
        
        val status = tokenStore.getTokenStatus(token)
        if (status != TokenStatus.ACTIVE) {
            throw TokenActivationException("활성화된 토큰이 아닙니다. 현재 상태: $status")
        }
        
        return waitingToken
    }
    
    /**
     * 예약/결제 완료 시 호출 - 즉시 다음 사용자 활성화
     */
    fun completeReservation(token: String) {
        val waitingToken = tokenStore.findByToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        
        // 토큰 만료
        tokenStore.expireToken(token)
        
        // 즉시 다음 사용자 활성화 (이벤트 기반)
        fillAvailableSlots()
    }
    
    /**
     * 스케줄러에서 호출 - 안전장치 역할
     */
    fun processQueueAutomatically() {
        // 1. 먼저 만료된 활성 토큰들 정리
        cleanupExpiredActiveTokens()
        
        // 2. 그 다음 가용 슬롯 채우기
        fillAvailableSlots()
    }
    
    /**
     * 만료된 활성 토큰들 정리 (타임아웃, 브라우저 종료 등)
     */
    fun cleanupExpiredActiveTokens() {
        val expiredTokens = tokenStore.findExpiredActiveTokens()
        expiredTokens.forEach { expiredToken ->
            try {
                tokenStore.expireToken(expiredToken)
                println("만료된 활성 토큰 정리: $expiredToken")
            } catch (e: Exception) {
                println("토큰 만료 처리 실패: $expiredToken, 오류: ${e.message}")
            }
        }
    }
    
    /**
     * 가용 슬롯을 다음 대기자들로 채우기
     */
    private fun fillAvailableSlots() {
        val currentActiveCount = tokenStore.countActiveTokens()
        val availableSlots = (MAX_ACTIVE_TOKENS - currentActiveCount).toInt()
        
        if (availableSlots > 0) {
            val tokensToActivate = tokenStore.getNextTokensFromQueue(availableSlots)
            
            tokensToActivate.forEach { tokenString ->
                try {
                    val waitingToken = tokenStore.findByToken(tokenString)
                    if (waitingToken != null) {
                        tokenStore.activateToken(tokenString)
                        println("토큰 자동 활성화: $tokenString")
                    }
                } catch (e: Exception) {
                    println("토큰 활성화 실패: $tokenString, 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 관리용 - 큐 상태 조회
     */
    fun getQueueStatus(): QueueStatusResponse {
        val queueSize = tokenStore.getQueueSize()
        val activeCount = tokenStore.countActiveTokens()
        val availableSlots = MAX_ACTIVE_TOKENS - activeCount
        
        return QueueStatusResponse(
            queueSize = queueSize,
            activeTokens = activeCount,
            maxActiveTokens = MAX_ACTIVE_TOKENS,
            availableSlots = availableSlots
        )
    }
    
    private fun generateToken(): String = UUID.randomUUID().toString()
}

// ===== 간소화된 응답 객체들 =====

data class TokenStatusResponse(
    val status: TokenStatus,
    val message: String
)

data class QueueStatusResponse(
    val queueSize: Long,
    val activeTokens: Long,
    val maxActiveTokens: Long,
    val availableSlots: Long
)

// ===== 예외 클래스들 =====

class TokenNotFoundException(message: String) : RuntimeException(message)
class TokenActivationException(message: String) : RuntimeException(message)
