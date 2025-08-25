package kr.hhplus.be.server.api.test.dto

import java.time.LocalDateTime

data class MockApiResponse(
    val success: Boolean,
    val message: String,
    val timestamp: LocalDateTime,
    val receivedDataId: Long? = null
)