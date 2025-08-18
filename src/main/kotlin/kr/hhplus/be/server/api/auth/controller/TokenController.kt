package kr.hhplus.be.server.api.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import kr.hhplus.be.server.api.auth.dto.TokenIssueDetail
import kr.hhplus.be.server.api.auth.dto.TokenQueueDetail
import kr.hhplus.be.server.api.auth.dto.request.TokenIssueRequest
import kr.hhplus.be.server.api.auth.usecase.TokenIssueUseCase
import kr.hhplus.be.server.api.auth.usecase.TokenQueueStatusUseCase
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/tokens")
@Validated
@Tag(name = "대기열 토큰", description = "대기열 토큰 관리 API")
class TokenController(
    private val tokenIssueUseCase: TokenIssueUseCase,
    private val tokenQueueStatusUseCase: TokenQueueStatusUseCase
) {
    
    @Operation(
        summary = "대기열 토큰 발급",
        description = "사용자에게 대기열 토큰을 발급합니다. 이미 활성 토큰이 있는 경우 기존 토큰 정보를 반환합니다."
    )
    @PostMapping
    fun issueToken(
        @Valid @RequestBody 
        @Parameter(description = "토큰 발급 요청", required = true) 
        request: TokenIssueRequest
    ): ResponseEntity<CommonApiResponse<TokenIssueDetail>> {
        val response = tokenIssueUseCase.execute(request.userId)
        
        return ResponseEntity.status(201).body(
            CommonApiResponse.success(
                data = response,
                message = "대기열 토큰이 성공적으로 발급되었습니다"
            )
        )
    }

    @Operation(
        summary = "토큰 대기열 상태 조회",
        description = "토큰의 현재 대기열 상태를 조회합니다."
    )
    @GetMapping("/{token}")
    fun getTokenStatus(
        @PathVariable 
        @Parameter(description = "조회할 토큰", required = true, example = "WT_1234567890ABCDEF")
        @NotBlank(message = "토큰은 필수입니다")
        token: String
    ): ResponseEntity<CommonApiResponse<TokenQueueDetail>> {
        val response = tokenQueueStatusUseCase.execute(token)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = response,
                message = "토큰 대기열 상태 조회가 완료되었습니다"
            )
        )
    }
}
