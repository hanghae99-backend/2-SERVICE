package kr.hhplus.be.server.domain.reservation.event.handler

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import kr.hhplus.be.server.domain.concert.service.ConcertDataPlatformService
import kr.hhplus.be.server.global.client.SeatApiClient
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ReservationEventHandler(
    private val selloutRankingService: SelloutRankingService,
    private val concertDataPlatformService: ConcertDataPlatformService,
    private val seatApiClient: SeatApiClient,
    private val reservationService: ReservationService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCreated(event: ReservationCreatedEvent) {
        try {
            // 1. 좌석 가용성 검증
            seatApiClient.validateSeatAvailability(event.seatId)
            
            // 2. 좌석 정보 조회 및 예약 정보 업데이트
            val seatInfo = seatApiClient.getSeatInfo(event.seatId)
            reservationService.updateReservationSeatInfo(
                reservationId = event.reservationId,
                seatNumber = seatInfo.seatNumber,
                price = seatInfo.price
            )
            
            // 3. 좌석 예약 처리
            seatApiClient.reserveSeat(event.seatId)
            
            // 4. 매진 순위 업데이트
            selloutRankingService.incrementReservationCount(event.concertId)
            
            // 5. 데이터 플랫폼 전송 (비동기)
            concertDataPlatformService.sendReservationData(
                reservationId = event.reservationId,
                userId = event.userId,
                concertId = event.concertId,
                seatId = event.seatId,
                paymentId = 0L,
                operationType = "RESERVATION_CREATED"
            )
            
            logger.info { "예약 생성 이벤트 처리 완료 - reservationId: ${event.reservationId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "예약 생성 이벤트 처리 실패 - reservationId: ${event.reservationId}" }
            // TODO: 예약 취소 보상 트랜잭션 구현
            throw e
        }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCancelled(event: ReservationCancelledEvent) {
        try {
            // 매진 순위 업데이트
            selloutRankingService.decrementReservationCount(event.concertId)
            
            // 데이터 플랫폼 전송 (비동기)
            concertDataPlatformService.sendReservationData(
                reservationId = event.reservationId,
                userId = event.userId,
                concertId = event.concertId,
                seatId = event.seatId,
                paymentId = 0L,
                operationType = "RESERVATION_CANCELLED"
            )
            
            logger.info { "예약 취소 이벤트 처리 완료 - reservationId: ${event.reservationId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "예약 취소 이벤트 처리 실패 - reservationId: ${event.reservationId}" }
        }
    }
}
