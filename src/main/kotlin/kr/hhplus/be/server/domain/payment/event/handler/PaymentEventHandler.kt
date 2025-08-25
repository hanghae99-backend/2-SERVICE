package kr.hhplus.be.server.domain.payment.event.handler

import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.concert.service.ConcertDataPlatformService
import kr.hhplus.be.server.domain.concert.service.ConcertReservationData
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
open class PaymentEventHandler(
    private val concertDataPlatformService: ConcertDataPlatformService,
    private val seatService: SeatService,
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val reservationRepository: ReservationRepository
) {
    
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        sendReservationToDataPlatform(
            reservationId = event.reservationId,
            userId = event.userId,
            concertId = event.concertId,
            seatId = event.seatId,
            paymentId = event.paymentId
        )
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentFailed(event: PaymentFailedEvent) {
        logger.info { "결제 실패 처리 - paymentId: ${event.paymentId}, reason: ${event.reason}" }
    }

    @Async
    fun sendReservationToDataPlatform(
        reservationId: Long,
        userId: Long,
        concertId: Long,
        seatId: Long,
        paymentId: Long
    ) {
        try {
            val reservation = reservationRepository.findById(reservationId)
                ?: throw IllegalStateException("예약 정보를 찾을 수 없습니다: $reservationId")
            
            val seat = seatService.getSeatById(seatId)
            val schedule = concertScheduleRepository.findById(seat.scheduleId)
                ?: throw IllegalStateException("스케줄 정보를 찾을 수 없습니다: ${seat.scheduleId}")
            
            val concert = concertRepository.findById(schedule.concertId)
                ?: throw IllegalStateException("콘서트 정보를 찾을 수 없습니다: ${schedule.concertId}")
            
            val reservationData = ConcertReservationData(
                reservationId = reservationId,
                userId = userId,
                concertId = concertId,
                concertTitle = concert.title,
                scheduleId = seat.scheduleId,
                concertDate = schedule.concertDate.atStartOfDay(),
                venue = schedule.venue,
                seatId = seatId,
                seatNumber = seat.seatNumber,
                price = reservation.price,
                paymentId = paymentId,
                reservedAt = reservation.createdAt ?: java.time.LocalDateTime.now()
            )
            
            concertDataPlatformService.sendReservationData(reservationData)
            logger.info { "데이터 플랫폼 전송 성공 - reservationId: $reservationId" }
            
        } catch (e: Exception) {
            logger.error(e) { "데이터 플랫폼 전송 실패 - reservationId: $reservationId" }
        }
    }
}