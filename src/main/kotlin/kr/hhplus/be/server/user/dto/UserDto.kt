package kr.hhplus.be.server.user.dto

import kr.hhplus.be.server.user.entity.User

/**
 * User Domain DTOs
 * 사용자 관련 DTO 클래스들
 */

/**
 * 기본 사용자 DTO
 */
data class UserDto(
    val userId: Long
) {
    companion object {
        fun fromEntity(user: User): UserDto {
            return UserDto(
                userId = user.userId
            )
        }
    }
}



