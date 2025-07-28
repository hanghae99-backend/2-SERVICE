package kr.hhplus.be.server.domain.auth.factory

import kr.hhplus.be.server.domain.auth.models.WaitingToken
import org.springframework.stereotype.Component
import java.util.*

/**
 * 토큰 생성의 단일 책임을 가진다
 */
@Component
class TokenFactory {
    
    /**
     * 고유한 토큰 생성
     */
    fun createToken(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * 사용자 ID와 함께 대기 토큰 생성
     */
    fun createWaitingToken(userId: Long): WaitingToken {
        val token = createToken()
        return WaitingToken(token = token, userId = userId)
    }
}
