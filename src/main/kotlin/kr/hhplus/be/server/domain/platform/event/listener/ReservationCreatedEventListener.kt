package kr.hhplus.be.server.domain.platform.event.listener

import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ReservationCreatedEventListener(
    private val concertDataPlatformClient: ConcertDataPlatformClient
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Async
    @EventListener
    fun handle(event: ReservationCreatedEvent) {
        try {
            // 데이터 플랫폼에 예약 생성 정보 전송 (비동기)
            concertDataPlatformClient.sendReservationData(
                reservationId = event.reservationId,
                userId = event.userId,
                concertId = event.concertId,
                seatId = event.seatId,
                operationType = "RESERVATION_CREATED"
            )
            
            logger.info { "데이터 플랫폼 예약 생성 정보 전송 완료 - reservationId: ${event.reservationId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "데이터 플랫폼 예약 생성 정보 전송 실패 - reservationId: ${event.reservationId}" }
            // 외부 시스템 연동 실패는 핵심 비즈니스에 영향을 주지 않음
        }
    }
}