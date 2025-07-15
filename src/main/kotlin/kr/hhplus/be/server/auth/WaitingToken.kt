package kr.hhplus.be.server.auth

import java.time.LocalDateTime
import java.util.*

data class WaitingToken(
    val token: String,
    val userId: Long    ,
    val position: Long,
    val status: TokenStatus = TokenStatus.WAITING,
    val issuedAt: LocalDateTime = LocalDateTime.now(),
    val activatedAt: LocalDateTime? = null,
    val expiredAt: LocalDateTime? = null
) {
    
    fun activate(): WaitingToken {
        if (this.status != TokenStatus.WAITING) {
            throw IllegalStateException("대기 중인 토큰만 활성화할 수 있습니다.")
        }
        
        return this.copy(
            status = TokenStatus.ACTIVE,
            activatedAt = LocalDateTime.now()
        )
    }
    
    fun expire(): WaitingToken {
        return this.copy(
            status = TokenStatus.EXPIRED,
            expiredAt = LocalDateTime.now()
        )
    }
    
    fun isExpired(): Boolean {
        return this.status == TokenStatus.EXPIRED || 
               (this.expiredAt != null && LocalDateTime.now().isAfter(this.expiredAt))
    }
}

enum class TokenStatus {
    WAITING, ACTIVE, EXPIRED
}
