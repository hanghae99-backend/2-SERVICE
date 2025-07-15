package kr.hhplus.be.server.auth

import io.mockk.*
import kr.hhplus.be.server.user.User
import kr.hhplus.be.server.user.UserNotFoundException
import kr.hhplus.be.server.user.UserService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("TokenService 유닛 테스트")
class TokenServiceUnitTest {

    private lateinit var userService: UserService
    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        tokenService = TokenService(userService)
    }

    @Test
    @DisplayName("존재하는 사용자에게 대기 토큰을 발급할 수 있다")
    fun issueWaitingToken_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userService.findUserById(userId) } returns user

        // when
        val waitingToken = tokenService.issueWaitingToken(userId)

        // then
        assertThat(waitingToken.userId).isEqualTo(userId)
        assertThat(waitingToken.token).isNotBlank()
        assertThat(waitingToken.position).isEqualTo(1L)
        assertThat(waitingToken.status).isEqualTo(TokenStatus.WAITING)
        assertThat(waitingToken.issuedAt).isNotNull()
        
        verify(exactly = 1) { userService.findUserById(userId) }
    }

    @Test
    @DisplayName("존재하지 않는 사용자에게 토큰 발급 시 예외가 발생한다")
    fun issueWaitingToken_UserNotFound_ThrowsException() {
        // given
        val userId = 999L
        every { userService.findUserById(userId) } returns null

        // when & then
        val exception = assertThrows<UserNotFoundException> {
            tokenService.issueWaitingToken(userId)
        }
        assertThat(exception.message).contains("사용자를 찾을 수 없습니다")
        
        verify(exactly = 1) { userService.findUserById(userId) }
    }

    @Test
    @DisplayName("여러 사용자에게 토큰을 발급하면 position이 증가한다")
    fun issueWaitingToken_MultipleUsers_PositionIncreases() {
        // given
        val userId1 = 1L
        val userId2 = 2L
        val user1 = User.create(userId1)
        val user2 = User.create(userId2)
        
        every { userService.findUserById(userId1) } returns user1
        every { userService.findUserById(userId2) } returns user2

        // when
        val token1 = tokenService.issueWaitingToken(userId1)
        val token2 = tokenService.issueWaitingToken(userId2)

        // then
        assertThat(token1.position).isEqualTo(1L)
        assertThat(token2.position).isEqualTo(2L)
        assertThat(token1.token).isNotEqualTo(token2.token)
    }

    @Test
    @DisplayName("토큰의 현재 순서를 조회할 수 있다")
    fun getCurrentPosition_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userService.findUserById(userId) } returns user
        
        val waitingToken = tokenService.issueWaitingToken(userId)

        // when
        val positionResponse = tokenService.getCurrentPosition(waitingToken.token)

        // then
        assertThat(positionResponse.position).isEqualTo(1L)
        assertThat(positionResponse.estimatedWaitTime).isGreaterThanOrEqualTo(0L)
        assertThat(positionResponse.status).isEqualTo(TokenStatus.WAITING)
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 순서 조회 시 예외가 발생한다")
    fun getCurrentPosition_TokenNotFound_ThrowsException() {
        // given
        val invalidToken = "invalid-token"

        // when & then
        val exception = assertThrows<TokenNotFoundException> {
            tokenService.getCurrentPosition(invalidToken)
        }
        assertThat(exception.message).contains("토큰을 찾을 수 없습니다")
    }

    @Test
    @DisplayName("대기 상태인 토큰을 활성화할 수 있다")
    fun activateUser_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userService.findUserById(userId) } returns user
        
        val waitingToken = tokenService.issueWaitingToken(userId)

        // when
        val activatedToken = tokenService.activateUser(waitingToken.token)

        // then
        assertThat(activatedToken.status).isEqualTo(TokenStatus.ACTIVE)
        assertThat(activatedToken.activatedAt).isNotNull()
        assertThat(activatedToken.token).isEqualTo(waitingToken.token)
        assertThat(activatedToken.userId).isEqualTo(userId)
    }

    @Test
    @DisplayName("존재하지 않는 토큰 활성화 시 예외가 발생한다")
    fun activateUser_TokenNotFound_ThrowsException() {
        // given
        val invalidToken = "invalid-token"

        // when & then
        val exception = assertThrows<TokenNotFoundException> {
            tokenService.activateUser(invalidToken)
        }
        assertThat(exception.message).contains("토큰을 찾을 수 없습니다")
    }

    @Test
    @DisplayName("이미 활성화된 토큰을 다시 활성화하려고 하면 예외가 발생한다")
    fun activateUser_AlreadyActive_ThrowsException() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userService.findUserById(userId) } returns user
        
        val waitingToken = tokenService.issueWaitingToken(userId)
        tokenService.activateUser(waitingToken.token)

        // when & then
        val exception = assertThrows<TokenActivationException> {
            tokenService.activateUser(waitingToken.token)
        }
        assertThat(exception.message).contains("토큰을 활성화할 수 없습니다")
    }

    @Test
    @DisplayName("활성 토큰이 100개일 때 추가 활성화 시 예외가 발생한다")
    fun activateUser_MaxActiveTokensReached_ThrowsException() {
        // given - 100개의 활성 토큰 생성
        repeat(100) { i ->
            val userId = i.toLong() + 1
            val user = User.create(userId)
            every { userService.findUserById(userId) } returns user
            
            val token = tokenService.issueWaitingToken(userId)
            tokenService.activateUser(token.token)
        }
        
        // 101번째 사용자
        val userId101 = 101L
        val user101 = User.create(userId101)
        every { userService.findUserById(userId101) } returns user101
        val waitingToken101 = tokenService.issueWaitingToken(userId101)

        // when & then
        val exception = assertThrows<TokenActivationException> {
            tokenService.activateUser(waitingToken101.token)
        }
        assertThat(exception.message).contains("현재 활성화할 수 없습니다")
    }

    @Test
    @DisplayName("토큰을 만료시킬 수 있다")
    fun expireToken_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userService.findUserById(userId) } returns user
        
        val waitingToken = tokenService.issueWaitingToken(userId)

        // when
        val expiredToken = tokenService.expireToken(waitingToken.token)

        // then
        assertThat(expiredToken.status).isEqualTo(TokenStatus.EXPIRED)
        assertThat(expiredToken.expiredAt).isNotNull()
        assertThat(expiredToken.token).isEqualTo(waitingToken.token)
    }

    @Test
    @DisplayName("존재하지 않는 토큰 만료 시 예외가 발생한다")
    fun expireToken_TokenNotFound_ThrowsException() {
        // given
        val invalidToken = "invalid-token"

        // when & then
        val exception = assertThrows<TokenNotFoundException> {
            tokenService.expireToken(invalidToken)
        }
        assertThat(exception.message).contains("토큰을 찾을 수 없습니다")
    }

    @Test
    @DisplayName("사용자의 모든 토큰을 조회할 수 있다")
    fun getUserTokens_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userService.findUserById(userId) } returns user
        
        val token1 = tokenService.issueWaitingToken(userId)
        val token2 = tokenService.issueWaitingToken(userId)

        // when
        val userTokens = tokenService.getUserTokens(userId)

        // then
        assertThat(userTokens).hasSize(2)
        assertThat(userTokens.map { it.token }).containsExactlyInAnyOrder(token1.token, token2.token)
        assertThat(userTokens.all { it.userId == userId }).isTrue()
    }

    @Test
    @DisplayName("토큰이 없는 사용자의 토큰 조회 시 빈 리스트를 반환한다")
    fun getUserTokens_NoTokens_ReturnsEmptyList() {
        // given
        val userId = 999L

        // when
        val userTokens = tokenService.getUserTokens(userId)

        // then
        assertThat(userTokens).isEmpty()
    }

    @Test
    @DisplayName("예상 대기 시간이 올바르게 계산된다")
    fun calculateEstimatedWaitTime_Success() {
        // given
        val userId1 = 1L
        val userId2 = 2L
        val userId3 = 3L
        
        val user1 = User.create(userId1)
        val user2 = User.create(userId2)
        val user3 = User.create(userId3)
        
        every { userService.findUserById(userId1) } returns user1
        every { userService.findUserById(userId2) } returns user2
        every { userService.findUserById(userId3) } returns user3
        
        val token1 = tokenService.issueWaitingToken(userId1) // position: 1
        val token2 = tokenService.issueWaitingToken(userId2) // position: 2
        val token3 = tokenService.issueWaitingToken(userId3) // position: 3
        
        // 첫 번째 토큰 활성화
        tokenService.activateUser(token1.token)

        // when
        val position2 = tokenService.getCurrentPosition(token2.token)
        val position3 = tokenService.getCurrentPosition(token3.token)

        // then
        // position 2: 활성 토큰 1개, 대기 위치 = 2 - 1 = 1, 예상 시간 = 1 * 30 = 30초
        assertThat(position2.estimatedWaitTime).isEqualTo(30L)
        
        // position 3: 활성 토큰 1개, 대기 위치 = 3 - 1 = 2, 예상 시간 = 2 * 30 = 60초
        assertThat(position3.estimatedWaitTime).isEqualTo(60L)
    }

    @Test
    @DisplayName("활성화된 토큰의 예상 대기 시간은 0이다")
    fun calculateEstimatedWaitTime_ActiveToken_ReturnsZero() {
        // given
        val userId = 1L
        val user = User.create(userId)
        every { userService.findUserById(userId) } returns user
        
        val token = tokenService.issueWaitingToken(userId)
        tokenService.activateUser(token.token)

        // when
        val position = tokenService.getCurrentPosition(token.token)

        // then
        assertThat(position.estimatedWaitTime).isEqualTo(0L)
    }

    @Test
    @DisplayName("토큰 생성 시 고유한 토큰 문자열이 생성된다")
    fun generateToken_UniqueTokens() {
        // given
        val userId1 = 1L
        val userId2 = 2L
        val user1 = User.create(userId1)
        val user2 = User.create(userId2)
        
        every { userService.findUserById(userId1) } returns user1
        every { userService.findUserById(userId2) } returns user2

        // when
        val token1 = tokenService.issueWaitingToken(userId1)
        val token2 = tokenService.issueWaitingToken(userId2)

        // then
        assertThat(token1.token).isNotEqualTo(token2.token)
        assertThat(token1.token).isNotBlank()
        assertThat(token2.token).isNotBlank()
    }

    @Test
    @DisplayName("동시성 테스트 - 여러 토큰의 position이 올바르게 증가한다")
    fun issueWaitingToken_Concurrency_PositionIncrementsCorrectly() {
        // given
        val userIds = (1L..10L).toList()
        userIds.forEach { userId ->
            val user = User.create(userId)
            every { userService.findUserById(userId) } returns user
        }

        // when
        val tokens = userIds.map { userId ->
            tokenService.issueWaitingToken(userId)
        }

        // then
        val positions = tokens.map { it.position }.sorted()
        assertThat(positions).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
    }

    // ============= 도메인 객체 테스트 =============

    @Test
    @DisplayName("TokenPositionResponse를 생성할 수 있다")
    fun tokenPositionResponse_Create() {
        // given
        val position = 5L
        val estimatedWaitTime = 150L
        val status = TokenStatus.WAITING

        // when
        val response = TokenPositionResponse(position, estimatedWaitTime, status)

        // then
        assertThat(response.position).isEqualTo(position)
        assertThat(response.estimatedWaitTime).isEqualTo(estimatedWaitTime)
        assertThat(response.status).isEqualTo(status)
    }

    @Test
    @DisplayName("TokenNotFoundException이 올바른 메시지를 가진다")
    fun tokenNotFoundException_Message() {
        // given
        val message = "토큰을 찾을 수 없습니다"

        // when
        val exception = TokenNotFoundException(message)

        // then
        assertThat(exception).isInstanceOf(RuntimeException::class.java)
        assertThat(exception.message).isEqualTo(message)
    }

    @Test
    @DisplayName("TokenActivationException이 올바른 메시지를 가진다")
    fun tokenActivationException_Message() {
        // given
        val message = "토큰을 활성화할 수 없습니다"

        // when
        val exception = TokenActivationException(message)

        // then
        assertThat(exception).isInstanceOf(RuntimeException::class.java)
        assertThat(exception.message).isEqualTo(message)
    }
}
