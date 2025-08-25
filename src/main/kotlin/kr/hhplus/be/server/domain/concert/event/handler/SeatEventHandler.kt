package kr.hhplus.be.server.domain.concert.event.handler

import kr.hhplus.be.server.domain.concert.event.SeatConfirmedEvent
import kr.hhplus.be.server.domain.concert.event.SeatStatusChangedEvent
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.concert.service.ConcertDataPlatformService
import kr.hhplus.be.server.domain.concert.service.ConcertReservationData
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SeatEventHandler(
    private val seatService: SeatService,
    private val concertDataPlatformService: ConcertDataPlatformService,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val concertRepository: ConcertRepository,
    private val reservationRepository: ReservationRepository
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSeatConfirmed(event: SeatConfirmedEvent) {
        try {
            sendReservationToDataPlatform(event)
        } catch (e: Exception) {
            logger.error(e) { "좌석 확정 후처리 실패 - seatId: ${event.seatId}" }
        }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSeatStatusChanged(event: SeatStatusChangedEvent) {
    }
    
    private fun sendReservationToDataPlatform(event: SeatConfirmedEvent) {
        val reservation = reservationRepository.findById(event.reservationId)
            ?: throw IllegalStateException("예약 정보를 찾을 수 없습니다: ${event.reservationId}")
            
        val schedule = concertScheduleRepository.findById(event.scheduleId)
            ?: throw IllegalStateException("스케줄 정보를 찾을 수 없습니다: ${event.scheduleId}")
            
        val concert = concertRepository.findById(schedule.concertId)
            ?: throw IllegalStateException("콘서트 정보를 찾을 수 없습니다: ${schedule.concertId}")
        
        val seat = seatService.getSeatById(event.seatId)
        
        val reservationData = ConcertReservationData(
            reservationId = event.reservationId,
            userId = event.userId,
            concertId = concert.concertId,
            concertTitle = concert.title,
            scheduleId = event.scheduleId,
            concertDate = schedule.concertDate.atStartOfDay(),
            venue = schedule.venue,
            seatId = event.seatId,
            seatNumber = event.seatNumber,
            price = seat.price,
            paymentId = event.paymentId,
            reservedAt = reservation.createdAt ?: java.time.LocalDateTime.now()
        )
        
        concertDataPlatformService.sendReservationData(reservationData)
    }
}
