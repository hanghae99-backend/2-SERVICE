package kr.hhplus.be.server.domain.reservation.event.handler

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import kr.hhplus.be.server.domain.concert.service.ConcertDataPlatformService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ReservationEventHandler(
    private val selloutRankingService: SelloutRankingService,
    private val concertDataPlatformService: ConcertDataPlatformService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCreated(event: ReservationCreatedEvent) {
        // 매진 순위 업데이트
        handleSelloutRankingUpdate(event.concertId, increment = true)
        
        // 데이터 플랫폼 전송 (비동기)
        concertDataPlatformService.sendReservationData(
            reservationId = event.reservationId,
            userId = event.userId,
            concertId = event.concertId,
            seatId = event.seatId,
            paymentId = 0L, // 임시 예약 상태이므로 paymentId 없음
            operationType = "RESERVATION_CREATED"
        )
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCancelled(event: ReservationCancelledEvent) {
        // 매진 순위 업데이트
        handleSelloutRankingUpdate(event.concertId, increment = false)
        
        // 데이터 플랫폼 전송 (비동기)
        concertDataPlatformService.sendReservationData(
            reservationId = event.reservationId,
            userId = event.userId,
            concertId = event.concertId,
            seatId = event.seatId,
            paymentId = 0L, // 취소된 예약이므로 paymentId 없음
            operationType = "RESERVATION_CANCELLED"
        )
    }

    private fun handleSelloutRankingUpdate(concertId: Long, increment: Boolean) {
        try {
            if (increment) {
                selloutRankingService.incrementReservationCount(concertId)
                logger.debug { "매진 순위 증가 성공 - concertId: $concertId" }
            } else {
                selloutRankingService.decrementReservationCount(concertId)
                logger.debug { "매진 순위 감소 성공 - concertId: $concertId" }
            }
        } catch (e: Exception) {
            val operation = if (increment) "증가" else "감소"
            logger.error(e) { "매진 순위 업데이트 실패 ($operation) - concertId: $concertId, error: ${e.message}" }
        }
    }
}
