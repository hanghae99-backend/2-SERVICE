package kr.hhplus.be.server.domain.reservation.event.listener

import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import kr.hhplus.be.server.global.event.EventErrorHandler
import kr.hhplus.be.server.global.event.EventErrorHandling
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ReservationCreatedEventListener(
    private val concertDataPlatformClient: ConcertDataPlatformClient,
    private val errorHandler: EventErrorHandler
) {
    
    private val logger = KotlinLogging.logger {}
    
    @EventErrorHandling(sendToDLQ = false, critical = false)
    @Async
    @EventListener
    fun handle(event: ReservationCreatedEvent) {
        errorHandler.handleEventSafely(event, "ReservationCreatedEvent") {
            concertDataPlatformClient.sendReservationData(
                reservationId = event.reservationId,
                userId = event.userId,
                concertId = event.concertId,
                seatId = event.seatId,
                operationType = "RESERVATION_CREATED"
            )
            logger.info { "데이터 플랫폼 예약 생성 정보 전송 완료: ${event.reservationId}" }
        }
    }
}