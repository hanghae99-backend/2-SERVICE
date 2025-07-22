package kr.hhplus.be.server.auth.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 통합 토큰 응답 DTO
 * 토큰 발급, 상태 조회, 대기열 조회 등 모든 토큰 관련 응답에 사용
 */
@Schema(description = "토큰 응답")
data class TokenResponse(
    @Schema(description = "토큰", example = "token-uuid-1234")
    val token: String,

    @Schema(description = "토큰 상태", example = "WAITING")
    val status: String,

    @Schema(description = "상태 메시지", example = "대기 중입니다")
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

        fun createWaiting(
        token: String,
        message: String = "대기 중입니다"
        ): TokenResponse {
        return TokenResponse(
        token = token,
        status = "WAITING",
        message = message
        )
        }
        
        fun createActive(
        token: String,
        message: String = "서비스 이용 가능합니다"
        ): TokenResponse {
        return TokenResponse(
        token = token,
        status = "ACTIVE",
        message = message
        )
        }
        
        fun createExpired(
        token: String,
        message: String = "토큰이 만료되었습니다"
        ): TokenResponse {
        return TokenResponse(
        token = token,
        status = "EXPIRED",
        message = message
        )
        }
    }

    /**
     * 토큰 발급 시 상세 정보
     */
    @Schema(description = "토큰 발급 상세 응답")
    data class Issue(
        @Schema(description = "기본 토큰 정보")
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

        @Schema(description = "토큰 발급 시간", example = "2024-01-01T00:00:00")
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
            ): Issue {
                return Issue(
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
     * 대기열 상태 조회 시 상세 정보
     */
    @Schema(description = "대기열 상태 상세 응답")
    data class Queue(
        @Schema(description = "기본 토큰 정보")
        val token: String,

        @Schema(description = "토큰 상태")
        val status: String,

        @Schema(description = "상태 메시지")
        val message: String,

        @Schema(description = "대기 순서 (대기 중일 때만 제공)", example = "5")
        val queuePosition: Int? = null,

        @Schema(description = "예상 대기 시간 (분) (대기 중일 때만 제공)", example = "10")
        val estimatedWaitingTime: Int? = null
    ) {
        companion object {
            fun fromTokenWithQueue(
                token: String,
                status: String,
                message: String,
                queuePosition: Int? = null,
                estimatedWaitingTime: Int? = null
            ): Queue {
                return Queue(
                    token = token,
                    status = status,
                    message = message,
                    queuePosition = queuePosition,
                    estimatedWaitingTime = estimatedWaitingTime
                )
            }

            fun createWaitingWithQueue(
                token: String,
                queuePosition: Int,
                estimatedWaitingTime: Int,
                message: String = "대기 중입니다"
            ): Queue {
                return Queue(
                    token = token,
                    status = "WAITING",
                    message = message,
                    queuePosition = queuePosition,
                    estimatedWaitingTime = estimatedWaitingTime
                )
            }

            fun createActiveWithoutQueue(
                token: String,
                message: String = "서비스 이용 가능합니다"
            ): Queue {
                return Queue(
                    token = token,
                    status = "ACTIVE",
                    message = message
                )
            }
        }
    }
}