package kr.hhplus.be.server.domain.auth.factory

import kr.hhplus.be.server.domain.auth.models.WaitingToken
import org.springframework.stereotype.Component
import java.util.*


@Component
class TokenFactory {
    

    fun createToken(): String {
        return UUID.randomUUID().toString()
    }
    

    fun createWaitingToken(userId: Long): WaitingToken {
        val token = createToken()
        return WaitingToken(token = token, userId = userId)
    }
}
