package kr.hhplus.be.server.global.event

import java.time.LocalDateTime

/**
 * 모든 도메인 이벤트의 기본 인터페이스
 */
interface DomainEvent {
    val eventId: String
    val occurredAt: LocalDateTime
    val eventType: String
}

/**
 * 추상 도메인 이벤트 클래스
 */
abstract class AbstractDomainEvent(
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: LocalDateTime = LocalDateTime.now()
) : DomainEvent