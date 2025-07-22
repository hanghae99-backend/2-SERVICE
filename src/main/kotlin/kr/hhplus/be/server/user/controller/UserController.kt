package kr.hhplus.be.server.user.controller

import com.hbd.book_be.dto.UserDto
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.user.dto.request.UserCreateRequest
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
    fun createUser(@RequestBody @Valid  userCreateRequest : UserCreateRequest) : ResponseEntity<UserDto> {
        val userDto = userService.createUser(userCreateRequest)
        return ResponseEntity.status(201).body(userDto)
    }

    /**
     * 사용자 정보 조회
     */
    @GetMapping("/{userId}")
    fun getUser(
        @PathVariable
        @Parameter(
            description = "유저 아이디",
            required = true
        )
        @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<UserDto.Detail> {
        val userDto = userService.getUserById(userId)
        return ResponseEntity.ok(userDto)
    }
}