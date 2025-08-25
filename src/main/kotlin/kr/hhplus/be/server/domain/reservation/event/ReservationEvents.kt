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
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val cancelReason: String,
    val isExpired: Boolean = false
) : AbstractDomainEvent() {
    override val eventType: String = "ReservationCancelled"
}

data class ReservationExpiredEvent(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val seatId: Long
) : AbstractDomainEvent() {
    override val eventType: String = "ReservationExpired"
}
