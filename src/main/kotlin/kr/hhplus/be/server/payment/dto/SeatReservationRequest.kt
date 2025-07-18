package kr.hhplus.be.server.payment.dto

import java.time.LocalDateTime

data class SeatReservationRequest(
    val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val token: String
)

data class SeatReservationResponse(
    val reservationId: Long,
    val userId: Long,
    val seatId: Long,
    val status: String,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime?,
    val message: String
) {
    companion object {
        fun from(reservation: kr.hhplus.be.server.payment.entity.Reservation): SeatReservationResponse {
            return SeatReservationResponse(
                reservationId = reservation.reservationId,
                userId = reservation.userId,
                seatId = reservation.seatId,
                status = reservation.status.name,
                createdAt = reservation.createdAt,
                expiresAt = reservation.expiresAt,
                message = when (reservation.status) {
                    kr.hhplus.be.server.payment.entity.ReservationStatus.TEMPORARY -> 
                        "좌석이 임시 배정되었습니다. ${reservation.expiresAt}까지 결제를 완료해주세요."
                    kr.hhplus.be.server.payment.entity.ReservationStatus.CONFIRMED -> 
                        "좌석 예약이 확정되었습니다."
                    kr.hhplus.be.server.payment.entity.ReservationStatus.CANCELLED -> 
                        "좌석 예약이 취소되었습니다."
                }
            )
        }
    }
}
