package kr.hhplus.be.server.user.controller.mock

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.user.entity.User
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/mock/users")
@Profile("mock")
@Tag(name = "Mock 사용자", description = "사용자 관리 Mock API (개발/테스트용)")
class MockUserController {

    // Mock 데이터 저장소
    private val mockUsers = mutableMapOf<Long, User>()
    
    init {
        // 초기 Mock 데이터 생성
        mockUsers[1L] = User(userId = 1L)
        mockUsers[2L] = User(userId = 2L)
        mockUsers[3L] = User(userId = 3L)
    }

    @PostMapping
    @Operation(
        summary = "사용자 생성 (Mock)",
        description = """
            **Mock 환경에서 사용자를 생성합니다**
            
            ## Mock 동작
            - 실제 데이터베이스 연동 없이 메모리에서 관리
            - 즉시 사용자 생성 완료
            - ID 중복 검사 수행
            
            ## 테스트 시나리오
            - 정상 사용자 생성
            - 중복 ID 에러 테스트
            - 잘못된 파라미터 테스트
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "사용자 생성 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = User::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (유효하지 않은 사용자 ID)"
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 존재하는 사용자 ID"
            )
        ]
    )
    fun createUser(@RequestParam @Parameter(description = "생성할 사용자 ID", example = "1") userId: Long): ResponseEntity<MockUserCreateResponse> {
        // 파라미터 검증
        if (userId <= 0) {
            return ResponseEntity.badRequest().build()
        }
        
        // 중복 검사
        if (mockUsers.containsKey(userId)) {
            return ResponseEntity.status(409).build()
        }
        
        // Mock 사용자 생성
        val user = User(userId = userId)
        mockUsers[userId] = user
        
        val response = MockUserCreateResponse(
            id = user.userId,
            message = "Mock 사용자가 성공적으로 생성되었습니다",
            createdAt = user.createdAt.toString(),
            isMock = true
        )
        
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{userId}")
    @Operation(
        summary = "사용자 조회 (Mock)",
        description = "Mock 환경에서 특정 사용자 정보를 조회합니다"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "사용자 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MockUserResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음"
            )
        ]
    )
    fun getUserById(@PathVariable @Parameter(description = "조회할 사용자 ID") userId: Long): ResponseEntity<MockUserResponse> {
        val user = mockUsers[userId]
            ?: return ResponseEntity.notFound().build()
        
        val response = MockUserResponse(
            id = user.userId,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString(),
            isMock = true,
            message = "Mock 데이터에서 조회된 사용자입니다"
        )
        
        return ResponseEntity.ok(response)
    }

    @GetMapping
    @Operation(
        summary = "전체 사용자 목록 조회 (Mock)",
        description = "Mock 환경에서 생성된 모든 사용자 목록을 조회합니다"
    )
    fun getAllUsers(): ResponseEntity<List<MockUserResponse>> {
        val userList = mockUsers.values.map { user ->
            MockUserResponse(
                id = user.userId,
                createdAt = user.createdAt.toString(),
                updatedAt = user.updatedAt.toString(),
                isMock = true,
                message = "Mock 사용자"
            )
        }
        
        return ResponseEntity.ok(userList)
    }

    @DeleteMapping("/{userId}")
    @Operation(
        summary = "사용자 삭제 (Mock)",
        description = "Mock 환경에서 특정 사용자를 삭제합니다"
    )
    fun deleteUser(@PathVariable @Parameter(description = "삭제할 사용자 ID") userId: Long): ResponseEntity<MockDeleteResponse> {
        val removedUser = mockUsers.remove(userId)
            ?: return ResponseEntity.notFound().build()
        
        val response = MockDeleteResponse(
            deletedUserId = userId,
            message = "Mock 사용자가 성공적으로 삭제되었습니다",
            deletedAt = LocalDateTime.now().toString(),
            isMock = true
        )
        
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/all")
    @Operation(
        summary = "모든 사용자 초기화 (Mock)",
        description = "Mock 환경의 모든 사용자 데이터를 초기화합니다 (테스트 환경 리셋용)"
    )
    fun resetAllUsers(): ResponseEntity<MockResetResponse> {
        val deletedCount = mockUsers.size
        mockUsers.clear()
        
        // 기본 Mock 데이터 재생성
        mockUsers[1L] = User(userId = 1L)
        mockUsers[2L] = User(userId = 2L)
        mockUsers[3L] = User(userId = 3L)
        
        val response = MockResetResponse(
            deletedCount = deletedCount,
            recreatedCount = mockUsers.size,
            message = "Mock 사용자 데이터가 초기화되었습니다",
            resetAt = LocalDateTime.now().toString(),
            isMock = true
        )
        
        return ResponseEntity.ok(response)
    }

    @GetMapping("/status")
    @Operation(
        summary = "Mock 서비스 상태 확인",
        description = "Mock 사용자 서비스의 현재 상태를 확인합니다"
    )
    fun getMockStatus(): ResponseEntity<MockStatusResponse> {
        val response = MockStatusResponse(
            totalUsers = mockUsers.size,
            userIds = mockUsers.keys.toList(),
            message = "Mock 사용자 서비스가 정상 동작 중입니다",
            isMock = true,
            timestamp = LocalDateTime.now().toString()
        )
        
        return ResponseEntity.ok(response)
    }
}

// Mock 응답 DTO들
@Schema(description = "Mock 사용자 생성 응답")
data class MockUserCreateResponse(
    @Schema(description = "생성된 사용자 ID", example = "1")
    val id: Long,
    
    @Schema(description = "응답 메시지", example = "Mock 사용자가 성공적으로 생성되었습니다")
    val message: String,
    
    @Schema(description = "생성 시간", example = "2024-01-01T00:00:00")
    val createdAt: String,
    
    @Schema(description = "Mock 데이터 여부", example = "true")
    val isMock: Boolean
)

@Schema(description = "Mock 사용자 조회 응답")
data class MockUserResponse(
    @Schema(description = "사용자 ID", example = "1")
    val id: Long,
    
    @Schema(description = "생성 시간", example = "2024-01-01T00:00:00")
    val createdAt: String,
    
    @Schema(description = "수정 시간", example = "2024-01-01T00:00:00")
    val updatedAt: String,
    
    @Schema(description = "Mock 데이터 여부", example = "true")
    val isMock: Boolean,
    
    @Schema(description = "응답 메시지", example = "Mock 데이터에서 조회된 사용자입니다")
    val message: String
)

@Schema(description = "Mock 사용자 삭제 응답")
data class MockDeleteResponse(
    @Schema(description = "삭제된 사용자 ID", example = "1")
    val deletedUserId: Long,
    
    @Schema(description = "응답 메시지", example = "Mock 사용자가 성공적으로 삭제되었습니다")
    val message: String,
    
    @Schema(description = "삭제 시간", example = "2024-01-01T00:00:00")
    val deletedAt: String,
    
    @Schema(description = "Mock 데이터 여부", example = "true")
    val isMock: Boolean
)

@Schema(description = "Mock 데이터 초기화 응답")
data class MockResetResponse(
    @Schema(description = "삭제된 사용자 수", example = "5")
    val deletedCount: Int,
    
    @Schema(description = "재생성된 사용자 수", example = "3")
    val recreatedCount: Int,
    
    @Schema(description = "응답 메시지", example = "Mock 사용자 데이터가 초기화되었습니다")
    val message: String,
    
    @Schema(description = "초기화 시간", example = "2024-01-01T00:00:00")
    val resetAt: String,
    
    @Schema(description = "Mock 데이터 여부", example = "true")
    val isMock: Boolean
)

@Schema(description = "Mock 서비스 상태 응답")
data class MockStatusResponse(
    @Schema(description = "총 사용자 수", example = "3")
    val totalUsers: Int,
    
    @Schema(description = "사용자 ID 목록", example = "[1, 2, 3]")
    val userIds: List<Long>,
    
    @Schema(description = "상태 메시지", example = "Mock 사용자 서비스가 정상 동작 중입니다")
    val message: String,
    
    @Schema(description = "Mock 데이터 여부", example = "true")
    val isMock: Boolean,
    
    @Schema(description = "조회 시간", example = "2024-01-01T00:00:00")
    val timestamp: String
)
