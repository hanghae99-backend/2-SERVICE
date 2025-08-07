package kr.hhplus.be.server.global.event

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component


@Component
class DomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) {


    fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }


    fun publishAll(events: List<DomainEvent>) {
        events.forEach { publish(it) }
    }
}