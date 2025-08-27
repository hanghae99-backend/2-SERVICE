package kr.hhplus.be.server.global.queue

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class RedisDeadLetterQueueService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        private const val DLQ_KEY_PREFIX = "dlq"
        private const val PROCESSING_KEY_PREFIX = "dlq:processing"
    }
    fun <T : Any> send(
        event: T,
        exception: Exception,
        maxRetries: Int = 3,
        correlationId: String? = null
    ) {
        try {
            val dlqItem = DeadLetterQueueItem.create(
                eventType = event::class.java.simpleName,
                eventPayload = objectMapper.writeValueAsString(event),
                exception = exception,
                maxRetries = maxRetries,
                correlationId = correlationId
            )
            
            val dlqKey = getDLQKey(dlqItem.eventType)
            val serializedItem = objectMapper.writeValueAsString(dlqItem)
            
            redisTemplate.opsForList().leftPush(dlqKey, serializedItem)
            
            logger.warn { "DLQ 전송: ${dlqItem.eventType} - ${dlqItem.failureReason}" }
            
        } catch (e: Exception) {
            logger.error(e) { "DLQ 전송 실패: ${event::class.java.simpleName}" }
        }
    }
    
    @Scheduled(fixedDelay = 300000)
    fun processDeadLetterQueue() {
        val eventTypes = getEventTypes()
        var processedCount = 0
        
        for (eventType in eventTypes) {
            processedCount += processEventType(eventType)
        }
        
        if (processedCount > 0) {
            logger.info { "DLQ 처리 완료: ${processedCount}개" }
        }
    }
    
    private fun processEventType(eventType: String): Int {
        val dlqKey = getDLQKey(eventType)
        var processedCount = 0
        
        while (true) {
            val serializedItem = redisTemplate.opsForList().rightPop(dlqKey) ?: break
            
            try {
                val dlqItem = objectMapper.readValue(serializedItem, DeadLetterQueueItem::class.java)
                
                if (!dlqItem.canRetry()) {
                    if (!dlqItem.isFinalFailure()) {
                        redisTemplate.opsForList().leftPush(dlqKey, serializedItem)
                    } else {
                        handleFinalFailure(dlqItem)
                    }
                    continue
                }
                
                val processingKey = getProcessingKey(eventType, dlqItem.correlationId ?: "unknown")
                if (redisTemplate.opsForValue().setIfAbsent(processingKey, "processing") != true) {
                    redisTemplate.opsForList().leftPush(dlqKey, serializedItem)
                    continue
                }
                
                val success = reprocessEvent(dlqItem)
                
                if (success) {
                    logger.info { "DLQ 재처리 성공: ${dlqItem.eventType}" }
                    processedCount++
                } else {
                    val retriedItem = dlqItem.incrementRetry()
                    val retriedSerialized = objectMapper.writeValueAsString(retriedItem)
                    redisTemplate.opsForList().leftPush(dlqKey, retriedSerialized)
                    logger.warn { "DLQ 재처리 실패: ${dlqItem.eventType} (${retriedItem.retryCount}/${retriedItem.maxRetries})" }
                }
                
                redisTemplate.delete(processingKey)
                
            } catch (e: Exception) {
                logger.error(e) { "DLQ 아이템 처리 오류" }
                redisTemplate.opsForList().leftPush("dlq:corrupted", serializedItem)
            }
        }
        
        return processedCount
    }
    
    private fun reprocessEvent(dlqItem: DeadLetterQueueItem): Boolean {
        return try {
            when (dlqItem.eventType) {
                "BalanceRestoreRequiredEvent" -> {
                    logger.info { "BalanceRestoreRequiredEvent 재처리" }
                    true
                }
                else -> {
                    logger.warn { "알 수 없는 이벤트 타입: ${dlqItem.eventType}" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "이벤트 재처리 실패: ${dlqItem.eventType}" }
            false
        }
    }
    
    private fun handleFinalFailure(dlqItem: DeadLetterQueueItem) {
        logger.error { "DLQ 최종 실패: ${dlqItem.eventType} - ${dlqItem.failureReason}" }
        
        redisTemplate.opsForList().leftPush(
            "dlq:final_failures",
            objectMapper.writeValueAsString(dlqItem)
        )
    }
    private fun getDLQKey(eventType: String): String = "$DLQ_KEY_PREFIX:$eventType"
    private fun getProcessingKey(eventType: String, id: String): String = "$PROCESSING_KEY_PREFIX:$eventType:$id"
    
    private fun getEventTypes(): List<String> {
        val keys = redisTemplate.keys("$DLQ_KEY_PREFIX:*") ?: emptySet()
        return keys.map { it.substringAfter("$DLQ_KEY_PREFIX:") }
    }
}