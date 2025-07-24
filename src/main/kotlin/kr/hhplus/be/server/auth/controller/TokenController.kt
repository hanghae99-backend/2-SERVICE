package kr.hhplus.be.server.auth.controller

import io.swagger.v3.oas.annotations.*
import io.swagger.v3.oas.annotations.media.*
import io.swagger.v3.oas.annotations.responses.*
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.hhplus.be.server.auth.dto.TokenDto
import kr.hhplus.be.server.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.auth.dto.request.TokenIssueRequest
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "토큰 발급 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenIssueDetail::class)
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
        ]
    )
    fun issueToken(@Valid @RequestBody @Parameter(description = "토큰 발급 요청") request: TokenIssueRequest): ResponseEntity<TokenIssueDetail> {
        val response = tokenService.issueWaitingToken(request.userId)
        return ResponseEntity.status(201).body(response)
    }

    @GetMapping("/{token}")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "상태 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenQueueDetail::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "토큰을 찾을 수 없음"
            )
        ]
    )
    fun getTokenStatus(@PathVariable @Parameter(description = "조회할 토큰") token: String): ResponseEntity<TokenQueueDetail> {
        val response = tokenService.getTokenQueueStatus(token)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{token}/status")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "간단 상태 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "토큰을 찾을 수 없음"
            )
        ]
    )
    fun getSimpleTokenStatus(@PathVariable @Parameter(description = "조회할 토큰") token: String): ResponseEntity<TokenDto> {
        val response = tokenService.getSimpleTokenStatus(token)
        return ResponseEntity.ok(response)
    }
}
