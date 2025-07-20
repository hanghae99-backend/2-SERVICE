package kr.hhplus.be.server.user.controller

import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Validated
class UserController(
    private val userService: UserService
) {
    /**
     * 사용자 생성
     */
    @PostMapping
    fun createUser(@RequestParam userId: Long): ResponseEntity<User> {
        val user = userService.createUser(userId)
        return ResponseEntity.status(201).body(user)
    }

    /**
     * 사용자 정보 조회
     */
    @GetMapping("/{userId}")
    fun getUser(
        @PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<User> {
        val user = userService.getUserById(userId)
        return ResponseEntity.ok(user)
    }
}