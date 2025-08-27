package kr.hhplus.be.server.domain.platform.event.listener

import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PaymentCompletedEventListener(
    private val concertDataPlatformClient: ConcertDataPlatformClient
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Async
    @EventListener
    fun handle(event: PaymentCompletedEvent) {
        try {
            // 데이터 플랫폼에 결제 완료 정보 전송 (비동기)
            concertDataPlatformClient.sendPaymentData(
                paymentId = event.paymentId,
                userId = event.userId,
                reservationId = event.reservationId,
                amount = event.amount,
                operationType = "PAYMENT_COMPLETED"
            )
            
            logger.info { "데이터 플랫폼 결제 완료 정보 전송 완료 - paymentId: ${event.paymentId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "데이터 플랫폼 결제 완료 정보 전송 실패 - paymentId: ${event.paymentId}" }
            // 외부 시스템 연동 실패는 핵심 비즈니스에 영향을 주지 않음
        }
    }
}