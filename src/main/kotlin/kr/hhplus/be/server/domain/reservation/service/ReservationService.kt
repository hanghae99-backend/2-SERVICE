package kr.hhplus.be.server.domain.reservation.service

import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationSearchCondition
import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationExpiredEvent
import kr.hhplus.be.server.domain.reservation.exception.ReservationNotFoundException
import kr.hhplus.be.server.domain.reservation.exception.ReservationAlreadyConfirmedException
import kr.hhplus.be.server.domain.reservation.exception.ReservationAccessDeniedException
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.concert.exception.SeatAlreadyReservedException
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val statusRepository: ReservationStatusTypePojoRepository,
    private val eventPublisher: DomainEventPublisher,
    private val seatService: SeatService
) {
    
    private val logger = LoggerFactory.getLogger(ReservationService::class.java)
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun reserveSeat(userId: Long, concertId: Long, seatId: Long): Reservation {
        logger.info("예약 요청 시작 - userId: {}, seatId: {}", userId, seatId)
        
        val seat = seatService.getSeatById(seatId)
        
        validateExistingReservation(seatId)
        seatService.reserveSeat(seatId)
        
        val reservation = createTemporaryReservation(userId, concertId, seatId, seat)
        publishReservationCreatedEvent(reservation)
        
        logger.info("예약 생성 성공 - reservationId: {}, userId: {}", reservation.reservationId, userId)
        return reservation
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun confirmReservation(reservationId: Long, paymentId: Long): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
            
        reservation.confirm(paymentId, statusRepository.getConfirmedStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        return savedReservation
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun cancelReservation(reservationId: Long, userId: Long, cancelReason: String?): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
        
        if (reservation.userId != userId) {
            throw ReservationAccessDeniedException(userId, reservationId)
        }
        
        return cancelReservationInternal(reservation, cancelReason ?: "사용자 취소", false)
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun cancelReservationBySystem(reservationId: Long, cancelReason: String): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
        
        val savedReservation = cancelReservationInternal(reservation, cancelReason, true)
        
        eventPublisher.publish(ReservationExpiredEvent(
            reservationId = savedReservation.reservationId,
            userId = savedReservation.userId,
            concertId = savedReservation.concertId,
            seatId = savedReservation.seatId
        ))
        
        return savedReservation
    }
    
    private fun cancelReservationInternal(reservation: Reservation, cancelReason: String, isExpired: Boolean): Reservation {
        reservation.cancel(statusRepository.getCancelledStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        eventPublisher.publish(ReservationCancelledEvent(
            reservationId = savedReservation.reservationId,
            userId = savedReservation.userId,
            concertId = savedReservation.concertId,
            seatId = savedReservation.seatId,
            cancelReason = cancelReason,
            isExpired = isExpired
        ))
        
        return savedReservation
    }
    
    fun getReservationById(reservationId: Long): Reservation {
        return reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
    }
    
    fun getReservationsByCondition(condition: ReservationSearchCondition): List<ReservationDto> {
        val reservations = when {
            condition.userId != null && condition.statusList != null -> {
                reservationRepository.findByUserIdAndStatusCodeInOrderByReservedAtDesc(
                    condition.userId, condition.statusList
                ).take(condition.limit)
            }
            condition.userId != null -> {
                reservationRepository.findByUserIdOrderByReservedAtDesc(condition.userId).take(condition.limit)
            }
            condition.concertId != null && condition.statusList != null -> {
                reservationRepository.findByConcertIdAndStatusCodeInOrderByReservedAtDesc(
                    condition.concertId, condition.statusList
                ).take(condition.limit)
            }
            condition.concertId != null -> {
                reservationRepository.findByConcertIdOrderByReservedAtDesc(condition.concertId).take(condition.limit)
            }
            condition.statusList != null -> {
                reservationRepository.findByStatusCodeInOrderByReservedAtDesc(condition.statusList).take(condition.limit)
            }
            else -> {
                reservationRepository.findAll().take(condition.limit)
            }
        }
        
        return reservations.map { ReservationDto.fromEntity(it) }
    }

    fun getExpiredReservations(limit: Int = 100): List<ReservationDto> {
        val expiredReservations = reservationRepository.findByExpiresAtBeforeAndStatusCode(
            LocalDateTime.now(), 
            statusRepository.getTemporaryStatus().code
        ).take(limit)
        
        return expiredReservations.map { ReservationDto.fromEntity(it) }
    }

    @LockGuard(
        key = "'reservation:cleanup'",
        strategy = LockStrategy.SIMPLE,
        waitTimeoutMs = 1000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun cleanupExpiredReservations(): Int {
        val expiredReservations = reservationRepository.findByExpiresAtBeforeAndStatusCode(
            LocalDateTime.now(),
            statusRepository.getTemporaryStatus().code
        )
        
        var cleanedCount = 0
        expiredReservations.forEach { reservation ->
            try {
                if (reservation.isExpired()) {
                    cancelReservationBySystem(reservation.reservationId, "예약 시간 만료")
                    cleanedCount++
                }
            } catch (e: Exception) {
                logger.warn("Failed to cleanup expired reservation: {}", reservation.reservationId, e)
            }
        }
        
        return cleanedCount
    }
    
    private fun validateExistingReservation(seatId: Long) {
        val activeStatuses = listOf(
            statusRepository.getTemporaryStatus().code,
            statusRepository.getConfirmedStatus().code
        )
        
        val existingReservation = reservationRepository.findBySeatIdAndStatusCodeIn(seatId, activeStatuses)
        
        if (existingReservation != null) {
            logger.warn("기존 예약 존재 - reservationId: {}, status: {}", existingReservation.reservationId, existingReservation.status.code)
            
            if (existingReservation.isConfirmed()) {
                throw ReservationAlreadyConfirmedException(existingReservation.reservationId)
            }
            
            if (existingReservation.isTemporary() && !existingReservation.isExpired()) {
                throw SeatAlreadyReservedException(seatId)
            }
        }
    }
    
    private fun createTemporaryReservation(userId: Long, concertId: Long, seatId: Long, seat: SeatDto): Reservation {
        val reservation = Reservation.createTemporary(
            userId = userId,
            concertId = concertId,
            seatId = seatId,
            seatNumber = seat.seatNumber,
            price = seat.price,
            temporaryStatus = statusRepository.getTemporaryStatus()
        )
        
        return reservationRepository.save(reservation)
    }
    
    private fun publishReservationCreatedEvent(reservation: Reservation) {
        eventPublisher.publish(ReservationCreatedEvent(
            reservationId = reservation.reservationId,
            userId = reservation.userId,
            concertId = reservation.concertId,
            seatId = reservation.seatId,
            seatNumber = reservation.seatNumber,
            price = reservation.price,
            expiresAt = reservation.expiresAt
        ))
    }
    
}
