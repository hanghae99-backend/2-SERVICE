package kr.hhplus.be.server.global.event

import kr.hhplus.be.server.global.queue.RedisDeadLetterQueueService
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class EventErrorHandler(
    private val deadLetterQueueService: RedisDeadLetterQueueService
) {
    
    private val logger = KotlinLogging.logger {}
    
    fun <T : Any> handleEventSafely(
        event: T,
        eventType: String,
        operation: () -> Unit
    ) {
        try {
            operation()
        } catch (e: Exception) {
            handleEventError(event, e, eventType)
        }
    }
    
    fun <T : Any> handleEventError(
        event: T,
        exception: Exception,
        eventType: String,
        sendToDLQ: Boolean = true,
        maxRetries: Int = 3,
        critical: Boolean = false
    ) {
        logger.error(exception) { "$eventType 처리 실패" }
        
        if (sendToDLQ) {
            try {
                deadLetterQueueService.send(
                    event = event,
                    exception = exception,
                    maxRetries = maxRetries,
                    correlationId = "${eventType}_${System.currentTimeMillis()}"
                )
            } catch (dlqException: Exception) {
                logger.error(dlqException) { "DLQ 전송 실패: $eventType" }
            }
        }
        
        if (critical) {
            throw EventProcessingException("Critical event processing failed: $eventType", exception)
        }
    }
}

class EventProcessingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)