package com.hbd.book_be.dto

import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.reservation.entity.Reservation
import kr.hhplus.be.server.user.entity.User


data class UserDto(
    val id: Long,
) {
    companion object {
        fun fromEntity(user: User): UserDto {
            return UserDto(
                id = user.userId
            )
        }
    }

    data class Detail(
        val id: Long,
        val point: Point?,
        val pointHistoryList: List<PointHistory>,
        val reservationList: List<Reservation>,
        val paymentList: List<Payment>
    ){
        companion object {
            fun fromEntity(
                user: User
            ): Detail {
                val pointHistoryList = user.pointHistoryList

                val reservationList = user.reservationList

                val paymentList = user.paymentList

                return Detail(
                    id = user.userId,
                    point = user.point,
                    pointHistoryList = pointHistoryList,
                    reservationList = reservationList,
                    paymentList = paymentList
                )
            }
        }
    }
}