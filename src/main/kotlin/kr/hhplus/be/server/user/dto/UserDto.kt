package kr.hhplus.be.server.user.dto

import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.reservation.entity.Reservation
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

/**
 * 사용자 상세 정보 DTO
 */
data class UserDetail(
    val userId: Long,
    val point: Point?,
    val pointHistoryList: List<PointHistory>,
    val reservationList: List<Reservation>,
    val paymentList: List<Payment>
) {
    companion object {
        fun fromEntity(user: User): UserDetail {
            return UserDetail(
                userId = user.userId,
                point = user.point,
                pointHistoryList = user.pointHistoryList,
                reservationList = user.reservationList,
                paymentList = user.paymentList
            )
        }
    }
}


