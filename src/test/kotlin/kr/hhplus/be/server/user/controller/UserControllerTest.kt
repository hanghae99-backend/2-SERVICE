package kr.hhplus.be.server.user.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.api.user.controller.UserController
import kr.hhplus.be.server.api.user.dto.UserDto
import kr.hhplus.be.server.api.user.dto.request.UserCreateRequest
import kr.hhplus.be.server.domain.user.exception.UserAlreadyExistsException
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.global.exception.GlobalExceptionHandler
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@WebMvcTest(UserController::class)
class UserControllerTest : DescribeSpec({
    
    val userService = mockk<UserService>()
    val userController = UserController(userService)
    val mockMvc = MockMvcBuilders.standaloneSetup(userController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = ObjectMapper()
    
    describe("POST /api/v1/users") {
        context("유효한 사용자 생성 요청이 들어올 때") {
            it("사용자를 생성하고 201 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val userCreateRequest = UserCreateRequest(userId)
                val userDto = UserDto(userId)
                
                every { userService.createUser(userCreateRequest) } returns userDto
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userCreateRequest))
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(userId))
                    .andExpect(jsonPath("$.message").value("사용자 생성 성공"))
            }
        }
        
        context("이미 존재하는 사용자 ID로 생성 요청이 들어올 때") {
            it("409 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val userCreateRequest = UserCreateRequest(userId)
                
                every { userService.createUser(userCreateRequest) } throws 
                    UserAlreadyExistsException("이미 존재하는 사용자 ID입니다: $userId")
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userCreateRequest))
                )
                    .andExpect(status().isConflict)
            }
        }
        
        context("잘못된 형식의 요청이 들어올 때") {
            it("400 상태코드를 반환해야 한다") {
                // given
                val invalidRequest = """{"userId": -1}"""
                
                // when & then
                mockMvc.perform(
                    post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest)
                )
                    .andExpect(status().isBadRequest)
            }
        }
    }
    
    describe("GET /api/v1/users/{userId}") {
        context("존재하는 사용자 ID로 조회할 때") {
            it("사용자 정보를 반환하고 200 상태코드를 반환해야 한다") {
                // given
                val userId = 1L
                val userDto = UserDto(userId)
                
                every { userService.getUserDtoById(userId) } returns userDto
                
                // when & then
                mockMvc.perform(get("/api/v1/users/$userId"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(userId))
                    .andExpect(jsonPath("$.message").value("사용자 조회 성공"))
            }
        }
        
        context("존재하지 않는 사용자 ID로 조회할 때") {
            it("404 상태코드를 반환해야 한다") {
                // given
                val userId = 999L
                
                every { userService.getUserDtoById(userId) } throws 
                    UserNotFoundException("User with id $userId not found")
                
                // when & then
                mockMvc.perform(get("/api/v1/users/$userId"))
                    .andExpect(status().isNotFound)
            }
        }
    }
})
