package kr.hhplus.be.server.domain.platform.event.listener

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ReservationCancelledEventListener(
    private val concertDataPlatformClient: ConcertDataPlatformClient
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Async
    @EventListener
    fun handle(event: ReservationCancelledEvent) {
        try {
            // 데이터 플랫폼에 예약 취소 정보 전송 (비동기)
            concertDataPlatformClient.sendReservationData(
                reservationId = event.reservationId,
                userId = event.userId,
                concertId = event.concertId,
                seatId = event.seatId,
                paymentId = 0L,
                operationType = "RESERVATION_CANCELLED"
            )
            
            logger.info { "데이터 플랫폼 예약 취소 정보 전송 완료 - reservationId: ${event.reservationId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "데이터 플랫폼 예약 취소 정보 전송 실패 - reservationId: ${event.reservationId}" }
        }
    }
}