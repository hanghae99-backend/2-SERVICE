package kr.hhplus.be.server.auth.controller

import io.swagger.v3.oas.annotations.*
import io.swagger.v3.oas.annotations.media.*
import io.swagger.v3.oas.annotations.responses.*
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.hhplus.be.server.auth.dto.TokenIssueRequest
import kr.hhplus.be.server.auth.dto.TokenIssueResponse
import kr.hhplus.be.server.auth.dto.QueueStatusResponse
import kr.hhplus.be.server.auth.service.TokenService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/tokens")
@Tag(name = "대기열 토큰", description = "대기열 토큰 관리 API")
class TokenController(
    private val tokenService: TokenService
) {

    @PostMapping
    @Operation(
        summary = "대기열 토큰 발급",
        description = """
            **서비스 이용을 위한 대기열 토큰을 발급받습니다**
            
            ## 발급 과정
            1. 사용자 ID로 토큰 발급 요청
            2. 대기열에 순서대로 배치
            3. 대기 토큰 반환 (UUID + 순서 정보)
            
            ## 토큰 사용법
            - 발급받은 토큰으로 대기 상태 확인
            - 순서가 되면 자동으로 활성화
            - 활성 토큰으로 예약/결제 API 이용
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "토큰 발급 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenIssueResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (유효하지 않은 사용자 ID)"
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음"
            ),
            ApiResponse(
                responseCode = "503",
                description = "대기열이 가득 참"
            )
        ]
    )
    fun issueToken(@Valid @RequestBody @Parameter(description = "토큰 발급 요청") request: TokenIssueRequest): ResponseEntity<TokenIssueResponse> {
        val waitingToken = tokenService.issueWaitingToken(request.userId)
        val statusResponse = tokenService.getTokenStatus(waitingToken.token)
        
        val response = TokenIssueResponse(
            token = waitingToken.token,
            userId = waitingToken.userId,
            status = statusResponse.status.name,
            queuePosition = statusResponse.queuePosition ?: 0,
            issuedAt = java.time.LocalDateTime.now().toString(),
            estimatedWaitingTime = (statusResponse.queuePosition ?: 0) * 2 // 예상 대기 시간 (분)
        )
        
        return ResponseEntity.status(201).body(response)
    }

    @GetMapping("/{token}")
    @Operation(
        summary = "대기열 상태 조회",
        description = """
            **현재 대기열 상태를 조회합니다**
            
            ## 폴링 방식
            - 클라이언트가 주기적으로 상태 확인
            - 권장 폴링 간격: 5-10초
            
            ## 상태 종류
            - `WAITING`: 대기 중 (순서 + 예상 시간 제공)
            - `ACTIVE`: 서비스 이용 가능 (5분간 유효)
            - `EXPIRED`: 만료됨 (재발급 필요)
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
                description = "토큰을 찾을 수 없음"
            )
        ]
    )
    fun getTokenStatus(@PathVariable @Parameter(description = "조회할 토큰") token: String): ResponseEntity<QueueStatusResponse> {
        val statusResponse = tokenService.getTokenStatus(token)
        
        val response = QueueStatusResponse(
            token = token,
            status = statusResponse.status.name,
            message = statusResponse.message,
            queuePosition = statusResponse.queuePosition,
            estimatedWaitingTime = statusResponse.queuePosition?.let { it * 2 } // 예상 대기 시간 (분)
        )
        
        return ResponseEntity.ok(response)
    }
}
