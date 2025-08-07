package kr.hhplus.be.server.global.event

import java.time.LocalDateTime


interface DomainEvent {
    val eventId: String
    val occurredAt: LocalDateTime
    val eventType: String
}


abstract class AbstractDomainEvent(
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: LocalDateTime = LocalDateTime.now()
) : DomainEvent