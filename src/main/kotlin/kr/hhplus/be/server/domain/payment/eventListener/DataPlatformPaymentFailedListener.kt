package kr.hhplus.be.server.domain.payment.eventListener

import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import kr.hhplus.be.server.global.event.EventErrorHandler
import kr.hhplus.be.server.global.event.EventErrorHandling
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class DataPlatformPaymentFailedListener(
    private val concertDataPlatformClient: ConcertDataPlatformClient,
    private val errorHandler: EventErrorHandler
) {

    private val logger = KotlinLogging.logger {}

    @EventErrorHandling(sendToDLQ = false, critical = false)
    @Async
    @EventListener
    fun handle(event: PaymentFailedEvent) {
        errorHandler.handleEventSafely(event, "PaymentFailedEvent") {
            event.amount?.let { amount ->
                concertDataPlatformClient.sendPaymentData(
                    paymentId = event.paymentId,
                    userId = event.userId,
                    reservationId = event.reservationId,
                    amount = amount,
                    operationType = "PAYMENT_FAILED"
                )
            }
            logger.info { "데이터 플랫폼 결제 실패 정보 전송 완료: ${event.paymentId}" }
        }
    }
}