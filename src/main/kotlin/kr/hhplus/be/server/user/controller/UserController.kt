package kr.hhplus.be.server.user.controller

import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {
    @PostMapping
    fun create(@RequestParam userId: Long): ResponseEntity<User> {
        val user = userService.createUser(userId)
        return ResponseEntity.ok(user)
    }
}
