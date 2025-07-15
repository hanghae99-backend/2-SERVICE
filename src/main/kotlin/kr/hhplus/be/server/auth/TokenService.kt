package kr.hhplus.be.server.auth

import kr.hhplus.be.server.user.UserService
import kr.hhplus.be.server.user.UserNotFoundException
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class TokenService(
    private val userService: UserService
) {
    
    private val tokens = ConcurrentHashMap<String, WaitingToken>()
    private val maxPosition = AtomicLong(0)
    
    fun issueWaitingToken(userId: Long): WaitingToken {
        // 사용자 존재 여부 확인
        userService.findUserById(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        
        val nextPosition = maxPosition.incrementAndGet()
        val token = generateToken()
        val waitingToken = WaitingToken(token, userId, nextPosition)
        
        tokens[token] = waitingToken
        return waitingToken
    }
    
    fun getCurrentPosition(token: String): TokenPositionResponse {
        val waitingToken = tokens[token]
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        
        val currentActiveCount = countActiveTokens()
        val maxActiveCount = 100L
        val estimatedWaitTime = calculateEstimatedWaitTime(
            waitingToken.position, currentActiveCount, maxActiveCount
        )
        
        return TokenPositionResponse(
            waitingToken.position,
            estimatedWaitTime,
            waitingToken.status
        )
    }
    
    fun activateUser(token: String): WaitingToken {
        val waitingToken = tokens[token]
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        
        if (waitingToken.status != TokenStatus.WAITING) {
            throw TokenActivationException("토큰을 활성화할 수 없습니다.")
        }
        
        val currentActiveCount = countActiveTokens()
        if (currentActiveCount >= 100L) {
            throw TokenActivationException("현재 활성화할 수 없습니다.")
        }
        
        val activatedToken = waitingToken.activate()
        tokens[token] = activatedToken
        return activatedToken
    }
    
    fun expireToken(token: String): WaitingToken {
        val waitingToken = tokens[token]
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        
        val expiredToken = waitingToken.expire()
        tokens[token] = expiredToken
        return expiredToken
    }
    
    fun getUserTokens(userId: Long): List<WaitingToken> {
        return tokens.values.filter { it.userId == userId }
    }
    
    private fun countActiveTokens(): Long {
        return tokens.values.count { it.status == TokenStatus.ACTIVE }.toLong()
    }
    
    private fun calculateEstimatedWaitTime(position: Long, currentActiveCount: Long, maxActiveCount: Long): Long {
        if (position <= currentActiveCount) return 0L
        val waitingPosition = position - currentActiveCount
        return waitingPosition * 30 // 30초씩 대기
    }
    
    private fun generateToken(): String = UUID.randomUUID().toString()
}

data class TokenPositionResponse(
    val position: Long,
    val estimatedWaitTime: Long,
    val status: TokenStatus
)

class TokenNotFoundException(message: String) : RuntimeException(message)
class TokenActivationException(message: String) : RuntimeException(message)
