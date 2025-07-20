package kr.hhplus.be.server.payment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class SeatReservationRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    @Positive(message = "사용자 ID는 양수여야 합니다")
    val userId: Long,
    
    @NotNull(message = "콘서트 ID는 필수입니다")
    @Positive(message = "콘서트 ID는 양수여야 합니다")
    val concertId: Long,
    
    @NotNull(message = "좌석 ID는 필수입니다")
    @Positive(message = "좌석 ID는 양수여야 합니다")
    val seatId: Long,
    
    @NotBlank(message = "토큰은 필수입니다")
    @Size(min = 10, max = 100, message = "토큰 길이는 10-100자 사이여야 합니다")
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
