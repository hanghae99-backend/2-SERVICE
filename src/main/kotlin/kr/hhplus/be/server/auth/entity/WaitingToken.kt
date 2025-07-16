package kr.hhplus.be.server.auth.entity

data class WaitingToken(
    val token: String,
    val userId: Long
)

enum class TokenStatus {
    WAITING, ACTIVE, EXPIRED
}
