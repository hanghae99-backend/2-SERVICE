package kr.hhplus.be.server.api.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.global.response.CommonApiResponse
import kr.hhplus.be.server.api.user.dto.UserDto
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "User", description = "사용자 관리 API")
class UserController(
    private val userService: UserService
) {

    @Operation(
        summary = "사용자 생성",
        description = "새로운 사용자를 생성합니다. 생성과 동시에 포인트 계정도 함께 생성됩니다."
    )
    @PostMapping
    fun createUser(
        @RequestBody @Valid 
        @Parameter(description = "사용자 생성 요청", required = true)
        userCreateRequest: UserCreateRequest
    ): ResponseEntity<CommonApiResponse<UserDto>> {
        val userDto = userService.createUser(userCreateRequest)
        
        return ResponseEntity.status(201).body(
            CommonApiResponse.success(
                data = userDto,
                message = "사용자 생성 성공"
            )
        )
    }

    @Operation(
        summary = "사용자 정보 조회",
        description = "특정 사용자의 상세 정보를 조회합니다."
    )
    @GetMapping("/{userId}")
    fun getUser(
        @PathVariable
        @Parameter(description = "사용자 ID", required = true, example = "1")
        @Positive(message = "사용자 ID는 양수여야 합니다") 
        userId: Long
    ): ResponseEntity<CommonApiResponse<UserDto>> {
        val userDto = userService.getUserDtoById(userId)
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = userDto,
                message = "사용자 조회 성공"
            )
        )
    }
}
