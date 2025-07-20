package kr.hhplus.be.server.auth.dto

import kr.hhplus.be.server.auth.entity.TokenStatus

data class TokenStatusResponse(
    val status: TokenStatus,
    val message: String,
    val queuePosition: Int? = null // 대기 순서 (WAITING 상태일 때만 제공)
)