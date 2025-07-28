package kr.hhplus.be.server.api.auth.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.hhplus.be.server.api.auth.dto.TokenDto
import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.domain.auth.service.TokenService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
                    schema = Schema(implementation = CommonApiResponse::class)
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
    fun issueToken(@Valid @RequestBody @Parameter(description = "토큰 발급 요청") request: TokenIssueRequest): ResponseEntity<CommonApiResponse<TokenIssueDetail>> {
        val response = tokenService.issueWaitingToken(request.userId)
        return ResponseEntity.status(201).body(
            CommonApiResponse.Companion.success(
                data = response,
                message = "대기열 토큰이 성공적으로 발급되었습니다"
            )
        )
    }

    @GetMapping("/{token}")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "상태 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CommonApiResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "토큰을 찾을 수 없음"
            )
        ]
    )
    fun getTokenStatus(@PathVariable @Parameter(description = "조회할 토큰") token: String): ResponseEntity<CommonApiResponse<TokenQueueDetail>> {
        val response = tokenService.getTokenQueueStatus(token)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = response,
                message = "토큰 대기열 상태 조회가 완료되었습니다"
            )
        )
    }

    @GetMapping("/{token}/status")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "간단 상태 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CommonApiResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "토큰을 찾을 수 없음"
            )
        ]
    )
    fun getSimpleTokenStatus(@PathVariable @Parameter(description = "조회할 토큰") token: String): ResponseEntity<CommonApiResponse<TokenDto>> {
        val response = tokenService.getSimpleTokenStatus(token)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = response,
                message = "토큰 상태 조회가 완료되었습니다"
            )
        )
    }
}