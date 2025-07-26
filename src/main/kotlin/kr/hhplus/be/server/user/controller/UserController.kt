package kr.hhplus.be.server.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import kr.hhplus.be.server.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.user.dto.UserDto
import kr.hhplus.be.server.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import kr.hhplus.be.server.global.response.CommonApiResponse

@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "User", description = "사용자 관리 API")
class UserController(
    private val userService: UserService
) {
    /**
     * 사용자 생성
     */
    @PostMapping
    @Operation(summary = "사용자 생성", description = "새로운 사용자를 생성합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "생성 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
        ApiResponse(responseCode = "409", description = "이미 존재하는 사용자"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    fun createUser(@RequestBody @Valid  userCreateRequest : UserCreateRequest) : ResponseEntity<CommonApiResponse<UserDto>> {
        val userDto = userService.createUser(userCreateRequest)
        return ResponseEntity.status(201).body(
            CommonApiResponse.success(
                data = userDto,
                message = "사용자 생성 성공"
            )
        )
    }

    /**
     * 사용자 정보 조회
     */
    @GetMapping("/{userId}")
    @Operation(summary = "사용자 상세 조회", description = "특정 사용자의 상세 정보를 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
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
            CommonApiResponse.success(
                data = UserDto,
                message = "사용자 조회 성공"
            )
        )
    }
}