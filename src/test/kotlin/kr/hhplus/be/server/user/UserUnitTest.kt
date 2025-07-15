package kr.hhplus.be.server.user

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("User 엔티티 유닛 테스트")
class UserUnitTest {

    @Test
    @DisplayName("User.create()로 사용자를 생성할 수 있다")
    fun create_Success() {
        // given
        val userId = 1L

        // when
        val user = User.create(userId)

        // then
        assertThat(user.userId).isEqualTo(userId)
        assertThat(user.createdAt).isNotNull()
        assertThat(user.updatedAt).isNotNull()
        assertThat(user.createdAt).isBeforeOrEqualTo(LocalDateTime.now())
        assertThat(user.updatedAt).isBeforeOrEqualTo(LocalDateTime.now())
    }

    @Test
    @DisplayName("User 생성자로 직접 사용자를 생성할 수 있다")
    fun constructor_Success() {
        // given
        val userId = 1L
        val createdAt = LocalDateTime.now().minusHours(1)
        val updatedAt = LocalDateTime.now()

        // when
        val user = User(userId, createdAt, updatedAt)

        // then
        assertThat(user.userId).isEqualTo(userId)
        assertThat(user.createdAt).isEqualTo(createdAt)
        assertThat(user.updatedAt).isEqualTo(updatedAt)
    }

    @Test
    @DisplayName("같은 userId를 가진 User는 동등하다")
    fun equals_SameUserId() {
        // given
        val userId = 1L
        val user1 = User.create(userId)
        val user2 = User.create(userId)

        // when & then
        assertThat(user1).isEqualTo(user2)
        assertThat(user1.hashCode()).isEqualTo(user2.hashCode())
    }

    @Test
    @DisplayName("다른 userId를 가진 User는 동등하지 않다")
    fun equals_DifferentUserId() {
        // given
        val userId1 = 1L
        val userId2 = 2L
        val user1 = User.create(userId1)
        val user2 = User.create(userId2)

        // when & then
        assertThat(user1).isNotEqualTo(user2)
        assertThat(user1.hashCode()).isNotEqualTo(user2.hashCode())
    }

    @Test
    @DisplayName("User toString()이 올바르게 동작한다")
    fun toString_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)

        // when
        val result = user.toString()

        // then
        assertThat(result).contains(userId.toString())
        assertThat(result).contains("User")
    }

    @Test
    @DisplayName("copy()로 User를 복사할 수 있다")
    fun copy_Success() {
        // given
        val userId = 1L
        val user = User.create(userId)
        val newUpdatedAt = LocalDateTime.now().plusHours(1)

        // when
        val copiedUser = user.copy(updatedAt = newUpdatedAt)

        // then
        assertThat(copiedUser.userId).isEqualTo(user.userId)
        assertThat(copiedUser.createdAt).isEqualTo(user.createdAt)
        assertThat(copiedUser.updatedAt).isEqualTo(newUpdatedAt)
        assertThat(copiedUser.updatedAt).isNotEqualTo(user.updatedAt)
    }

    @Test
    @DisplayName("createdAt과 updatedAt이 올바르게 설정된다")
    fun timestamps_Success() {
        // given
        val before = LocalDateTime.now()

        // when
        val user = User.create(1L)

        // then
        val after = LocalDateTime.now()
        assertThat(user.createdAt).isBetween(before, after)
        assertThat(user.updatedAt).isBetween(before, after)
        assertThat(user.createdAt).isEqualTo(user.updatedAt)
    }

    @Test
    @DisplayName("다양한 Long 값으로 User를 생성할 수 있다")
    fun create_VariousLongValues() {
        // given & when & then
        val user1 = User.create(1L)
        assertThat(user1.userId).isEqualTo(1L)

        val user2 = User.create(999L)
        assertThat(user2.userId).isEqualTo(999L)

        val user3 = User.create(Long.MAX_VALUE)
        assertThat(user3.userId).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    @DisplayName("음수 userId로도 User를 생성할 수 있다")
    fun create_NegativeUserId() {
        // given
        val userId = -1L

        // when
        val user = User.create(userId)

        // then
        assertThat(user.userId).isEqualTo(-1L)
    }

    @Test
    @DisplayName("0으로도 User를 생성할 수 있다")
    fun create_ZeroUserId() {
        // given
        val userId = 0L

        // when
        val user = User.create(userId)

        // then
        assertThat(user.userId).isEqualTo(0L)
    }
}
