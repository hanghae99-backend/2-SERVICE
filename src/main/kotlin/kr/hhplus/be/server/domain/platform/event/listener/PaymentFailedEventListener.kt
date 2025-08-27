package kr.hhplus.be.server.domain.platform.event.listener

import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PaymentFailedEventListener(
    private val concertDataPlatformClient: ConcertDataPlatformClient
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Async
    @EventListener
    fun handle(event: PaymentFailedEvent) {
        try {
            // 데이터 플랫폼에 결제 실패 정보 전송 (비동기)
            event.amount?.let { amount ->
                concertDataPlatformClient.sendPaymentData(
                    paymentId = event.paymentId,
                    userId = event.userId,
                    reservationId = event.reservationId,
                    amount = amount,
                    operationType = "PAYMENT_FAILED"
                )
            }
            
            logger.info { "데이터 플랫폼 결제 실패 정보 전송 완료 - paymentId: ${event.paymentId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "데이터 플랫폼 결제 실패 정보 전송 실패 - paymentId: ${event.paymentId}" }
        }
    }
}