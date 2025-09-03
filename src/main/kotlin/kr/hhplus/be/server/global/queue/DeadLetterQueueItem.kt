package kr.hhplus.be.server.global.queue

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import kotlin.math.pow

data class DeadLetterQueueItem(
    val eventType: String,
    val eventPayload: String,
    val failureReason: String,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val failedAt: LocalDateTime = LocalDateTime.now(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val nextRetryAt: LocalDateTime = calculateNextRetryTime(0),
    val correlationId: String? = null
) {
    
    companion object {
        fun calculateNextRetryTime(retryCount: Int): LocalDateTime {
            val delayMinutes = 2.0.pow(retryCount.toDouble()).toLong()
            return LocalDateTime.now().plusMinutes(delayMinutes)
        }
        
        fun create(
            eventType: String,
            eventPayload: String, 
            exception: Exception,
            maxRetries: Int = 3,
            correlationId: String? = null
        ): DeadLetterQueueItem {
            return DeadLetterQueueItem(
                eventType = eventType,
                eventPayload = eventPayload,
                failureReason = exception.message ?: exception::class.java.simpleName,
                maxRetries = maxRetries,
                correlationId = correlationId
            )
        }
    }
    
    fun canRetry(): Boolean {
        return retryCount < maxRetries && LocalDateTime.now().isAfter(nextRetryAt)
    }
    
    fun incrementRetry(): DeadLetterQueueItem {
        return this.copy(
            retryCount = retryCount + 1,
            nextRetryAt = calculateNextRetryTime(retryCount + 1)
        )
    }
    
    fun isFinalFailure(): Boolean {
        return retryCount >= maxRetries
    }
}