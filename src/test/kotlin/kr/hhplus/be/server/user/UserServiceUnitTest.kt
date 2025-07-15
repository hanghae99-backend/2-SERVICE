package kr.hhplus.be.server.user

import io.mockk.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

@DisplayName("UserService 유닛 테스트")
class UserServiceUnitTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        userService = UserService(userRepository)
    }

    @Test
    @DisplayName("새로운 사용자를 생성할 수 있다")
    fun createUser_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        
        every { userRepository.existsById(userId) } returns false
        every { userRepository.save(any<User>()) } returns user

        // when
        val result = userService.createUser(userId)

        // then
        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.createdAt).isNotNull()
        
        verify(exactly = 1) { userRepository.existsById(userId) }
        verify(exactly = 1) { userRepository.save(any<User>()) }
    }

    @Test
    @DisplayName("중복된 사용자 ID로 생성 시 예외가 발생한다")
    fun createUser_DuplicateId_ThrowsException() {
        // given
        val userId = 1L
        every { userRepository.existsById(userId) } returns true

        // when & then
        val exception = assertThrows<UserAlreadyExistsException> {
            userService.createUser(userId)
        }
        
        assertThat(exception.message).contains("이미 존재하는 사용자 ID입니다")
        
        verify(exactly = 1) { userRepository.existsById(userId) }
        verify(exactly = 0) { userRepository.save(any<User>()) }
    }

    @Test
    @DisplayName("사용자 ID로 조회할 수 있다")
    fun findUserById_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // when
        val result = userService.findUserById(userId)

        // then
        assertThat(result).isNotNull()
        assertThat(result!!.userId).isEqualTo(userId)
        
        verify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 null을 반환한다")
    fun findUserById_NotFound_ReturnsNull() {
        // given
        val userId = 999L
        every { userRepository.findById(userId) } returns Optional.empty()

        // when
        val result = userService.findUserById(userId)

        // then
        assertThat(result).isNull()
        
        verify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    @DisplayName("getUserById는 존재하는 사용자를 반환한다")
    fun getUserById_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // when
        val result = userService.getUserById(userId)

        // then
        assertThat(result.userId).isEqualTo(userId)
        
        verify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    @DisplayName("getUserById는 사용자가 없으면 예외를 발생시킨다")
    fun getUserById_NotFound_ThrowsException() {
        // given
        val userId = 999L
        every { userRepository.findById(userId) } returns Optional.empty()

        // when & then
        val exception = assertThrows<UserNotFoundException> {
            userService.getUserById(userId)
        }
        
        assertThat(exception.message).contains("사용자를 찾을 수 없습니다")
        
        verify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    @DisplayName("사용자를 삭제할 수 있다")
    fun deleteUser_Success() {
        // given
        val userId = 1L
        every { userRepository.existsById(userId) } returns true
        every { userRepository.deleteById(userId) } just Runs

        // when
        val result = userService.deleteUser(userId)

        // then
        assertThat(result).isTrue()
        
        verify(exactly = 1) { userRepository.existsById(userId) }
        verify(exactly = 1) { userRepository.deleteById(userId) }
    }

    @Test
    @DisplayName("존재하지 않는 사용자 삭제 시 false를 반환한다")
    fun deleteUser_NotFound_ReturnsFalse() {
        // given
        val userId = 999L
        every { userRepository.existsById(userId) } returns false

        // when
        val result = userService.deleteUser(userId)

        // then
        assertThat(result).isFalse()
        
        verify(exactly = 1) { userRepository.existsById(userId) }
        verify(exactly = 0) { userRepository.deleteById(any()) }
    }

    @Test
    @DisplayName("모든 사용자를 조회할 수 있다")
    fun getAllUsers_Success() {
        // given
        val users = listOf(
            User.create(1L),
            User.create(2L)
        )
        every { userRepository.findAll() } returns users

        // when
        val result = userService.getAllUsers()

        // then
        assertThat(result).hasSize(2)
        assertThat(result).isEqualTo(users)
        
        verify(exactly = 1) { userRepository.findAll() }
    }

    @Test
    @DisplayName("사용자 존재 여부를 확인할 수 있다")
    fun existsById_Success() {
        // given
        val userId = 1L
        every { userRepository.existsById(userId) } returns true

        // when
        val result = userService.existsById(userId)

        // then
        assertThat(result).isTrue()
        
        verify(exactly = 1) { userRepository.existsById(userId) }
    }

    @Test
    @DisplayName("사용자 수를 확인할 수 있다")
    fun getUserCount_Success() {
        // given
        every { userRepository.count() } returns 5L

        // when
        val result = userService.getUserCount()

        // then
        assertThat(result).isEqualTo(5L)
        
        verify(exactly = 1) { userRepository.count() }
    }

    @Test
    @DisplayName("경계값 테스트 - 최소값")
    fun createUser_MinValue() {
        // given
        val userId = 1L
        val user = User.create(userId)
        
        every { userRepository.existsById(userId) } returns false
        every { userRepository.save(any<User>()) } returns user

        // when
        val result = userService.createUser(userId)

        // then
        assertThat(result.userId).isEqualTo(1L)
    }

    @Test
    @DisplayName("경계값 테스트 - 큰 값")
    fun createUser_LargeValue() {
        // given
        val userId = Long.MAX_VALUE
        val user = User.create(userId)
        
        every { userRepository.existsById(userId) } returns false
        every { userRepository.save(any<User>()) } returns user

        // when
        val result = userService.createUser(userId)

        // then
        assertThat(result.userId).isEqualTo(Long.MAX_VALUE)
    }
}
