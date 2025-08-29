package kr.hhplus.be.server.domain.concert.eventListener

import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.global.event.EventErrorHandler
import kr.hhplus.be.server.global.event.EventErrorHandling
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SeatReleaseOnReservationCancelledListener(
    private val seatService: SeatService,
    private val errorHandler: EventErrorHandler
) {

    private val logger = KotlinLogging.logger {}

    @EventErrorHandling(sendToDLQ = true, critical = true)  // 좌석 해제는 중요하므로 critical = true
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCancelled(event: ReservationCancelledEvent) {
        errorHandler.handleEventSafely(event, "ReservationCancelledEvent") {
            // 좌석 해제 처리
            seatService.releaseSeat(event.seatId)

            val reasonDetails = when {
                event.isFailed -> "예약 생성 실패"
                event.isExpired -> "예약 시간 만료"
                else -> "사용자 취소"
            }

            logger.info {
                "좌석 해제 완료 - SeatId: ${event.seatId}, UserId: ${event.userId}, " +
                "사유: $reasonDetails (${event.cancelReason})" +
                if (event.reservationId != null) ", ReservationId: ${event.reservationId}" else ""
            }
        }
    }
}