package kr.hhplus.be.server.auth.controller

import kr.hhplus.be.server.auth.entity.WaitingToken
import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.auth.service.TokenStatusResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/tokens")
class TokenController(
    private val tokenService: TokenService
) {
    @PostMapping("/issue")
    fun issueToken(@RequestParam userId: Long): ResponseEntity<WaitingToken> {
        val token = tokenService.issueWaitingToken(userId)
        return ResponseEntity.ok(token)
    }

    @GetMapping("/status")
    fun getTokenStatus(@RequestParam token: String): ResponseEntity<TokenStatusResponse> {
        val status = tokenService.getTokenStatus(token)
        return ResponseEntity.ok(status)
    }
}
