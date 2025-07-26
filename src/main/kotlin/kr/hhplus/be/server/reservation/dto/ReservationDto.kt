package kr.hhplus.be.server.reservation.dto

import kr.hhplus.be.server.reservation.entity.Reservation
import kr.hhplus.be.server.reservation.entity.ReservationStatusType
import kr.hhplus.be.server.user.entity.User
import kr.hhplus.be.server.concert.entity.Concert
import kr.hhplus.be.server.concert.entity.Seat
import kr.hhplus.be.server.payment.entity.Payment
import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReservationDto(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val statusCode: String,
    val statusName: String,
    val statusDescription: String,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val reservedAt: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val expiresAt: LocalDateTime?,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val confirmedAt: LocalDateTime?
) {
    companion object {
        fun fromEntity(reservation: Reservation): ReservationDto {
            return ReservationDto(
                reservationId = reservation.reservationId,
                userId = reservation.userId,
                concertId = reservation.concertId,
                seatId = reservation.seatId,
                seatNumber = reservation.seatNumber,
                price = reservation.price,
                statusCode = reservation.statusCode,
                statusName = reservation.statusName,
                statusDescription = reservation.statusDescription,
                reservedAt = reservation.reservedAt,
                expiresAt = reservation.expiresAt,
                confirmedAt = reservation.confirmedAt
            )
        }

        
        private fun generateDefaultMessage(reservation: Reservation): String {
            return when (reservation.statusCode) {
                ReservationStatusType.TEMPORARY -> {
                    val expiresAt = reservation.expiresAt?.let { 
                        it.format(java.time.format.DateTimeFormatter.ofPattern("MM월 dd일 HH:mm"))
                    } ?: "정해진 시간"
                    "좌석이 임시 배정되었습니다. ${expiresAt}까지 결제를 완료해주세요."
                }
                ReservationStatusType.CONFIRMED -> "좌석 예약이 확정되었습니다."
                ReservationStatusType.CANCELLED -> "좌석 예약이 취소되었습니다."
                else -> "예약 상태: ${reservation.statusName}"
            }
        }
    }

    /**
     * 메시지가 포함된 예약 정보 (CRUD 작업용)
     */
    data class WithMessage(
        val reservation: ReservationDto,
        val message: String
    ) {
        companion object {
            fun fromEntity(reservation: Reservation, customMessage: String? = null): WithMessage {
                val dto = ReservationDto.fromEntity(reservation)
                return WithMessage(
                    reservation = dto,
                    message = customMessage ?: generateDefaultMessage(reservation)
                )
            }
        }
    }

    /**
     * 연관 엔티티를 포함한 상세 정보
     */
    data class Detail(
        val reservation: ReservationDto,
        val user: User?,
        val concert: Concert?,
        val seat: Seat?,
        val payment: Payment?
    ) {
        
        companion object {
            fun fromEntity(reservation: Reservation): Detail {
                return Detail(
                    reservation = ReservationDto.fromEntity(reservation),
                    user = reservation.user,
                    concert = reservation.concert,
                    seat = reservation.seat,
                    payment = reservation.payment
                )
            }
        }
    }

    /**
     * 페이징된 목록
     */
    data class Page(
        val reservations: List<ReservationDto>,
        val totalCount: Int,
        val pageNumber: Int,
        val pageSize: Int,
        val totalPages: Int
    ) {
        companion object {
            fun fromEntity(
                reservations: List<Reservation>,
                totalCount: Int,
                pageNumber: Int,
                pageSize: Int
            ): Page {
                return Page(
                    reservations = reservations.map { fromEntity(it) },
                    totalCount = totalCount,
                    pageNumber = pageNumber,
                    pageSize = pageSize,
                    totalPages = kotlin.math.ceil(totalCount.toDouble() / pageSize).toInt()
                )
            }
        }
    }

    /**
     * 예약 통계
     */
    data class Statistics(
        val totalReservations: Long,
        val temporaryReservations: Long,
        val confirmedReservations: Long,
        val cancelledReservations: Long,
        val expiredReservations: Long,
        val totalAmount: BigDecimal,
        @JsonFormat(pattern = "yyyy-MM-dd")
        val statisticsDate: LocalDateTime = LocalDateTime.now()
    )

    /**
     * 작업 결과 (정리, 배치 작업 등)
     */
    data class OperationResult(
        val message: String,
        val affectedCount: Int,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        val processedAt: LocalDateTime = LocalDateTime.now()
    )
}
