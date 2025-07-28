package kr.hhplus.be.server.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * Auth Domain DTOs
 * 토큰 관련 DTO 클래스들
 */

/**
 * 기본 토큰 DTO
 */
@Schema(description = "토큰 정보")
data class TokenDto(
    val token: String,
    val status: String,
    val message: String
) {
    companion object {
        fun create(
            token: String,
            status: String,
            message: String
        ): TokenDto {
            return TokenDto(
                token = token,
                status = status,
                message = message
            )
        }
    }
}

/**
 * 토큰 발급 응답용 DTO
 */
@Schema(description = "토큰 발급 상세 응답")
data class TokenIssueDetail(
    @Schema(description = "토큰")
    val token: String,
    
    @Schema(description = "토큰 상태")
    val status: String,
    
    @Schema(description = "상태 메시지")
    val message: String,
    
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "대기 순서", example = "5")
    val queuePosition: Int,
    
    @Schema(description = "예상 대기 시간 (분)", example = "10")
    val estimatedWaitingTime: Int,
    
    @Schema(description = "토큰 발급 시간")
    val issuedAt: LocalDateTime
) {
    companion object {
        fun fromTokenWithDetails(
            token: String,
            status: String,
            message: String,
            userId: Long,
            queuePosition: Int,
            estimatedWaitingTime: Int,
            issuedAt: LocalDateTime = LocalDateTime.now()
        ): TokenIssueDetail {
            return TokenIssueDetail(
                token = token,
                status = status,
                message = message,
                userId = userId,
                queuePosition = queuePosition,
                estimatedWaitingTime = estimatedWaitingTime,
                issuedAt = issuedAt
            )
        }
    }
}

/**
 * 대기열 상태 응답용 DTO
 */
@Schema(description = "대기열 상태 응답")
data class TokenQueueDetail(
    @Schema(description = "토큰")
    val token: String,
    
    @Schema(description = "토큰 상태")
    val status: String,
    
    @Schema(description = "상태 메시지")
    val message: String,
    
    @Schema(description = "대기 순서")
    val queuePosition: Int? = null,
    
    @Schema(description = "예상 대기 시간 (분)")
    val estimatedWaitingTime: Int? = null
) {
    companion object {
        fun fromTokenWithQueue(
            token: String,
            status: String,
            message: String,
            queuePosition: Int? = null,
            estimatedWaitingTime: Int? = null
        ): TokenQueueDetail {
            return TokenQueueDetail(
                token = token,
                status = status,
                message = message,
                queuePosition = queuePosition,
                estimatedWaitingTime = estimatedWaitingTime
            )
        }
    }
}
