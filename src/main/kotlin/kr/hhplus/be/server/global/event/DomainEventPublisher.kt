package kr.hhplus.be.server.global.event

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 도메인 이벤트 발행을 담당하는 클래스
 */
@Component
class DomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    
    /**
     * 도메인 이벤트를 발행합니다
     */
    fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
    
    /**
     * 여러 도메인 이벤트를 한 번에 발행합니다
     */
    fun publishAll(events: List<DomainEvent>) {
        events.forEach { publish(it) }
    }
}