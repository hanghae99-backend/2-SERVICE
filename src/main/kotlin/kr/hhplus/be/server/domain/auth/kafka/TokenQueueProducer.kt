package kr.hhplus.be.server.domain.auth.kafka

import kr.hhplus.be.server.domain.auth.models.WaitingToken
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class TokenQueueProducer(
    private val kafkaTemplate: KafkaTemplate<String, WaitingToken>
) {
    private val logger = KotlinLogging.logger {}
    
    companion object {
        private const val WAITING_TOKEN_TOPIC = "waiting-token"
    }
    
    fun sendTokenToQueue(waitingToken: WaitingToken) {
        try {
            kafkaTemplate.send(WAITING_TOKEN_TOPIC, 0, waitingToken.token, waitingToken)
                .thenAccept { result ->
                    logger.info { "토큰을 대기열 토픽에 전송 완료: token=${waitingToken.token}, userId=${waitingToken.userId}, offset=${result.recordMetadata.offset()}" }
                }
                .exceptionally { throwable ->
                    logger.error(throwable) { "토큰 대기열 전송 실패: token=${waitingToken.token}, userId=${waitingToken.userId}" }
                    null
                }
        } catch (e: Exception) {
            logger.error(e) { "토큰 대기열 전송 중 예외 발생: token=${waitingToken.token}, userId=${waitingToken.userId}" }
            throw e
        }
    }
    
    fun getTopicInfo(): String = WAITING_TOKEN_TOPIC
}