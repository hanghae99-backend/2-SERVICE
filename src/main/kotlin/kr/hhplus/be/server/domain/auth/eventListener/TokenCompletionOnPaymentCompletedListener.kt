package kr.hhplus.be.server.domain.auth.eventListener

import kr.hhplus.be.server.domain.auth.service.ActiveTokenService
import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TokenCompletionOnPaymentCompletedListener(
    private val activeTokenService: ActiveTokenService
) {

    private val logger = LoggerFactory.getLogger(TokenCompletionOnPaymentCompletedListener::class.java)

    @Async
    @EventListener
    @Transactional
    fun handle(event: PaymentCompletedEvent) {
        logger.info("결제 완료 이벤트 처리 시작: paymentId=${event.paymentId}")

        try {
            activeTokenService.completeToken(event.token)
            logger.info("토큰 완료 처리: token=${event.token}")

        } catch (e: Exception) {
            logger.error("결제 완료 이벤트 처리 실패: paymentId=${event.paymentId}, error=${e.message}", e)
        }
    }
}