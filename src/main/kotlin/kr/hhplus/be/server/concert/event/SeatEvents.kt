package kr.hhplus.be.server.concert.event

import kr.hhplus.be.server.global.event.AbstractDomainEvent

data class SeatConfirmedEvent(
    val seatId: Long,
    val scheduleId: Long,
    val seatNumber: String,
    val userId: Long,
    val reservationId: Long,
    val paymentId: Long
) : AbstractDomainEvent() {
    override val eventType: String = "SeatConfirmed"
}

data class SeatStatusChangedEvent(
    val seatId: Long,
    val scheduleId: Long,
    val seatNumber: String,
    val previousStatus: String,
    val newStatus: String
) : AbstractDomainEvent() {
    override val eventType: String = "SeatStatusChanged"
}
