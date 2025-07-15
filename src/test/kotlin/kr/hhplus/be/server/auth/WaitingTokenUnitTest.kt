package kr.hhplus.be.server.auth

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("WaitingToken 엔티티 유닛 테스트")
class WaitingTokenUnitTest {

    @Test
    @DisplayName("WaitingToken을 생성할 수 있다")
    fun create_Success() {
        // given
        val token = "test-token"
        val userId = 1L
        val position = 10L

        // when
        val waitingToken = WaitingToken(token, userId, position)

        // then
        assertThat(waitingToken.token).isEqualTo(token)
        assertThat(waitingToken.userId).isEqualTo(userId)
        assertThat(waitingToken.position).isEqualTo(position)
        assertThat(waitingToken.status).isEqualTo(TokenStatus.WAITING)
        assertThat(waitingToken.issuedAt).isNotNull()
        assertThat(waitingToken.activatedAt).isNull()
        assertThat(waitingToken.expiredAt).isNull()
    }

    @Test
    @DisplayName("WAITING 상태의 토큰을 활성화할 수 있다")
    fun activate_Success() {
        // given
        val waitingToken = WaitingToken("test-token", 1L, 1L, TokenStatus.WAITING)

        // when
        val activatedToken = waitingToken.activate()

        // then
        assertThat(activatedToken.status).isEqualTo(TokenStatus.ACTIVE)
        assertThat(activatedToken.activatedAt).isNotNull()
        assertThat(activatedToken.activatedAt).isBeforeOrEqualTo(LocalDateTime.now())
        assertThat(activatedToken.token).isEqualTo(waitingToken.token)
        assertThat(activatedToken.userId).isEqualTo(waitingToken.userId)
        assertThat(activatedToken.position).isEqualTo(waitingToken.position)
    }

    @Test
    @DisplayName("ACTIVE 상태의 토큰은 활성화할 수 없다")
    fun activate_AlreadyActive_ThrowsException() {
        // given
        val activeToken = WaitingToken("test-token", 1L, 1L, TokenStatus.ACTIVE)

        // when & then
        val exception = assertThrows<IllegalStateException> {
            activeToken.activate()
        }
        assertThat(exception.message).contains("대기 중인 토큰만 활성화할 수 있습니다")
    }

    @Test
    @DisplayName("EXPIRED 상태의 토큰은 활성화할 수 없다")
    fun activate_Expired_ThrowsException() {
        // given
        val expiredToken = WaitingToken("test-token", 1L, 1L, TokenStatus.EXPIRED)

        // when & then
        val exception = assertThrows<IllegalStateException> {
            expiredToken.activate()
        }
        assertThat(exception.message).contains("대기 중인 토큰만 활성화할 수 있습니다")
    }

    @Test
    @DisplayName("토큰을 만료시킬 수 있다")
    fun expire_Success() {
        // given
        val waitingToken = WaitingToken("test-token", 1L, 1L, TokenStatus.WAITING)

        // when
        val expiredToken = waitingToken.expire()

        // then
        assertThat(expiredToken.status).isEqualTo(TokenStatus.EXPIRED)
        assertThat(expiredToken.expiredAt).isNotNull()
        assertThat(expiredToken.expiredAt).isBeforeOrEqualTo(LocalDateTime.now())
        assertThat(expiredToken.token).isEqualTo(waitingToken.token)
        assertThat(expiredToken.userId).isEqualTo(waitingToken.userId)
        assertThat(expiredToken.position).isEqualTo(waitingToken.position)
    }

    @Test
    @DisplayName("ACTIVE 상태의 토큰도 만료시킬 수 있다")
    fun expire_ActiveToken_Success() {
        // given
        val activeToken = WaitingToken("test-token", 1L, 1L, TokenStatus.ACTIVE)

        // when
        val expiredToken = activeToken.expire()

        // then
        assertThat(expiredToken.status).isEqualTo(TokenStatus.EXPIRED)
        assertThat(expiredToken.expiredAt).isNotNull()
    }

    @Test
    @DisplayName("EXPIRED 상태인 토큰은 만료된 것으로 확인된다")
    fun isExpired_ExpiredStatus_ReturnsTrue() {
        // given
        val expiredToken = WaitingToken("test-token", 1L, 1L, TokenStatus.EXPIRED)

        // when & then
        assertThat(expiredToken.isExpired()).isTrue()
    }

    @Test
    @DisplayName("expiredAt이 현재 시간보다 이전인 토큰은 만료된 것으로 확인된다")
    fun isExpired_ExpiredTime_ReturnsTrue() {
        // given
        val pastTime = LocalDateTime.now().minusHours(1)
        val waitingToken = WaitingToken(
            "test-token", 1L, 1L, TokenStatus.WAITING,
            expiredAt = pastTime
        )

        // when & then
        assertThat(waitingToken.isExpired()).isTrue()
    }

    @Test
    @DisplayName("WAITING 상태이고 expiredAt이 null인 토큰은 만료되지 않은 것으로 확인된다")
    fun isExpired_WaitingWithoutExpiredAt_ReturnsFalse() {
        // given
        val waitingToken = WaitingToken("test-token", 1L, 1L, TokenStatus.WAITING)

        // when & then
        assertThat(waitingToken.isExpired()).isFalse()
    }

    @Test
    @DisplayName("ACTIVE 상태인 토큰은 만료되지 않은 것으로 확인된다")
    fun isExpired_ActiveStatus_ReturnsFalse() {
        // given
        val activeToken = WaitingToken("test-token", 1L, 1L, TokenStatus.ACTIVE)

        // when & then
        assertThat(activeToken.isExpired()).isFalse()
    }

    @Test
    @DisplayName("expiredAt이 미래 시간인 토큰은 만료되지 않은 것으로 확인된다")
    fun isExpired_FutureExpiredTime_ReturnsFalse() {
        // given
        val futureTime = LocalDateTime.now().plusHours(1)
        val waitingToken = WaitingToken(
            "test-token", 1L, 1L, TokenStatus.WAITING,
            expiredAt = futureTime
        )

        // when & then
        assertThat(waitingToken.isExpired()).isFalse()
    }

    @Test
    @DisplayName("copy()로 토큰의 일부 속성을 변경할 수 있다")
    fun copy_Success() {
        // given
        val originalToken = WaitingToken("test-token", 1L, 1L)
        val newStatus = TokenStatus.ACTIVE

        // when
        val copiedToken = originalToken.copy(status = newStatus)

        // then
        assertThat(copiedToken.status).isEqualTo(newStatus)
        assertThat(copiedToken.token).isEqualTo(originalToken.token)
        assertThat(copiedToken.userId).isEqualTo(originalToken.userId)
        assertThat(copiedToken.position).isEqualTo(originalToken.position)
    }

    @Test
    @DisplayName("같은 속성을 가진 토큰들은 동등하다")
    fun equals_SameProperties() {
        // given
        val token1 = WaitingToken("test-token", 1L, 1L)
        val token2 = WaitingToken("test-token", 1L, 1L)

        // when & then
        assertThat(token1).isEqualTo(token2)
        assertThat(token1.hashCode()).isEqualTo(token2.hashCode())
    }

    @Test
    @DisplayName("다른 속성을 가진 토큰들은 동등하지 않다")
    fun equals_DifferentProperties() {
        // given
        val token1 = WaitingToken("test-token-1", 1L, 1L)
        val token2 = WaitingToken("test-token-2", 1L, 1L)

        // when & then
        assertThat(token1).isNotEqualTo(token2)
    }

    @Test
    @DisplayName("issuedAt이 올바르게 설정된다")
    fun issuedAt_SetCorrectly() {
        // given
        val before = LocalDateTime.now()

        // when
        val token = WaitingToken("test-token", 1L, 1L)

        // then
        val after = LocalDateTime.now()
        assertThat(token.issuedAt).isBetween(before, after)
    }

    @Test
    @DisplayName("다양한 Long userId 값으로 토큰을 생성할 수 있다")
    fun create_VariousUserIds() {
        // given & when & then
        val token1 = WaitingToken("token1", 1L, 1L)
        assertThat(token1.userId).isEqualTo(1L)

        val token2 = WaitingToken("token2", 999L, 2L)
        assertThat(token2.userId).isEqualTo(999L)

        val token3 = WaitingToken("token3", Long.MAX_VALUE, 3L)
        assertThat(token3.userId).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    @DisplayName("음수 position으로도 토큰을 생성할 수 있다")
    fun create_NegativePosition() {
        // given
        val position = -1L

        // when
        val token = WaitingToken("test-token", 1L, position)

        // then
        assertThat(token.position).isEqualTo(-1L)
    }

    @Test
    @DisplayName("TokenStatus enum의 모든 값이 정의되어 있다")
    fun tokenStatus_AllValuesExist() {
        // when & then
        assertThat(TokenStatus.values()).hasSize(3)
        assertThat(TokenStatus.values()).containsExactlyInAnyOrder(
            TokenStatus.WAITING,
            TokenStatus.ACTIVE,
            TokenStatus.EXPIRED
        )
    }

    @Test
    @DisplayName("TokenStatus enum 값들이 올바른 이름을 가진다")
    fun tokenStatus_CorrectNames() {
        // when & then
        assertThat(TokenStatus.WAITING.name).isEqualTo("WAITING")
        assertThat(TokenStatus.ACTIVE.name).isEqualTo("ACTIVE")
        assertThat(TokenStatus.EXPIRED.name).isEqualTo("EXPIRED")
    }
}
