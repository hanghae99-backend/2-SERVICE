package kr.hhplus.be.server.api.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.global.response.CommonApiResponse
import kr.hhplus.be.server.api.user.dto.UserDto
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "User", description = "사용자 관리 API")
class UserController(
    private val userService: UserService
) {

    @PostMapping
    fun createUser(@RequestBody @Valid userCreateRequest : UserCreateRequest) : ResponseEntity<CommonApiResponse<UserDto>> {
        val userDto = userService.createUser(userCreateRequest)
        return ResponseEntity.status(201).body(
            CommonApiResponse.Companion.success(
                data = userDto,
                message = "사용자 생성 성공"
            )
        )
    }


    @GetMapping("/{userId}")
    fun getUser(
        @PathVariable
        @Parameter(
            description = "유저 아이디",
            required = true
        )
        @Positive(message = "사용자 ID는 양수여야 합니다") userId: Long
    ): ResponseEntity<CommonApiResponse<UserDto>> {
        val UserDto = userService.getUserDtoById(userId)
        return ResponseEntity.ok(
            CommonApiResponse.Companion.success(
                data = UserDto,
                message = "사용자 조회 성공"
            )
        )
    }
}