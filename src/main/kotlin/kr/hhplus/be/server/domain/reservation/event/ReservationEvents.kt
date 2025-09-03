package kr.hhplus.be.server.domain.reservation.event

import kr.hhplus.be.server.global.event.AbstractDomainEvent
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReservationCreatedEvent(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val expiresAt: LocalDateTime?
) : AbstractDomainEvent() {
    override val eventType: String = "ReservationCreated"
}

data class ReservationCancelledEvent(
    val reservationId: Long? = null,  // ReservationFailedEvent는 reservationId가 없을 수 있음
    val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val cancelReason: String,
    val isExpired: Boolean = false,
    val isFailed: Boolean = false    // 예약 생성 실패인지 구분
) : AbstractDomainEvent() {
    override val eventType: String = "ReservationCancelled"
}

