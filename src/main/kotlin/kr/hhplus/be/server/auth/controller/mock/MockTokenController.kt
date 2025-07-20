package kr.hhplus.be.server.auth.controller.mock

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.auth.dto.TokenIssueRequest
import kr.hhplus.be.server.auth.dto.TokenIssueResponse
import kr.hhplus.be.server.auth.dto.QueueStatusResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/mock/queue")
@Tag(name = "대기열 토큰 (Mock)", description = "대기열 토큰 관리 Mock API - 개발/테스트용")
class MockTokenController {

    @PostMapping("/tokens")
    @Operation(
        summary = "대기열 토큰 발급",
        description = """
            **사용자에게 대기열 토큰을 발급합니다**
            
            - 대기열에 진입하여 토큰을 발급받습니다
            - 발급된 토큰으로 예약 가능 여부를 확인할 수 있습니다
            - Mock 환경에서는 랜덤한 대기 순서가 할당됩니다
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "토큰 발급 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenIssueResponse::class),
                    examples = [
                        ExampleObject(
                            name = "성공 응답",
                            summary = "토큰 발급 성공 예시",
                            value = """{"token":"token-abc123","userId":1,"status":"WAITING","queuePosition":15,"issuedAt":"2024-01-01T10:00:00","estimatedWaitingTime":30}"""
                        )
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (userId가 0 이하인 경우)",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "잘못된 사용자 ID",
                            value = """{"error": "사용자 ID는 0보다 커야 합니다", "code": "INVALID_USER_ID"}"""
                        )
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "사용자 없음",
                            value = """{"error": "사용자를 찾을 수 없습니다", "code": "USER_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun issueToken(
        @RequestBody @Parameter(description = "토큰 발급 요청 정보") request: TokenIssueRequest
    ): ResponseEntity<TokenIssueResponse> {
        val token = "token-${UUID.randomUUID()}"
        val queuePosition = (1..100).random()

        val response = TokenIssueResponse(
            token = token,
            userId = request.userId,
            status = "WAITING",
            queuePosition = queuePosition,
            issuedAt = LocalDateTime.now().toString(),
            estimatedWaitingTime = queuePosition * 2
        )

        return ResponseEntity.ok(response)
    }

    @GetMapping("/tokens/{token}/status")
    @Operation(
        summary = "대기열 상태 조회",
        description = """
            **토큰의 현재 대기열 상태를 조회합니다**
            
            ## 상태 유형
            - `WAITING`: 대기 중 (queuePosition 포함)
            - `ACTIVE`: 예약 가능 (5분간 유효)
            - `EXPIRED`: 만료됨 (재발급 필요)
            
            ## 주의사항
            - 활성 토큰은 5분 후 자동 만료됩니다
            - 만료된 토큰은 재발급받아야 합니다
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "상태 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = QueueStatusResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "토큰을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "토큰 없음",
                            value = """{"error": "토큰을 찾을 수 없습니다", "code": "TOKEN_NOT_FOUND"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun getQueueStatus(
        @PathVariable @Parameter(description = "조회할 토큰 값", example = "token-abc123") token: String
    ): ResponseEntity<QueueStatusResponse> {
        val statuses = listOf("WAITING", "ACTIVE", "EXPIRED")
        val status = statuses.random()

        val response = QueueStatusResponse(
            token = token,
            status = status,
            message = when (status) {
                "WAITING" -> "대기 중입니다"
                "ACTIVE" -> "예약 가능합니다"
                "EXPIRED" -> "토큰이 만료되었습니다"
                else -> "알 수 없는 상태"
            },
            queuePosition = if (status == "WAITING") 1 else null,
            estimatedWaitingTime = if (status == "WAITING") 1 else null
        )

        return ResponseEntity.ok(response)
    }

    @PatchMapping("/tokens/{token}/activate")
    @Operation(
        summary = "토큰 활성화",
        description = """
            **대기열 토큰을 활성화합니다**
            
            ## 활성화 조건
            - 대기 순서가 되어 활성화 가능한 상태
            - 현재 활성 토큰 수가 최대 허용치 미만
            
            ## 활성화 후
            - 5분간 서비스 이용 가능
            - 예약 및 결제 API 사용 가능
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "활성화 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = QueueStatusResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "토큰을 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "토큰 없음",
                            value = """{"error": "토큰을 찾을 수 없습니다", "code": "TOKEN_NOT_FOUND"}"""
                        )
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "이미 활성화된 토큰이거나 만료된 토큰",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "활성화 실패",
                            value = """{"error": "이미 활성화된 토큰입니다", "code": "TOKEN_ALREADY_ACTIVE"}"""
                        )
                    ]
                )]
            )
        ]
    )
    fun activateToken(
        @PathVariable @Parameter(description = "활성화할 토큰", example = "token-abc123") token: String
    ): ResponseEntity<QueueStatusResponse> {
        val response = QueueStatusResponse(
            token = token,
            status = "ACTIVE",
            message = "토큰이 활성화되었습니다",
            queuePosition = null,
            estimatedWaitingTime = null
        )

        return ResponseEntity.ok(response)
    }
}
