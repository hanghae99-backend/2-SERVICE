package kr.hhplus.be.server.domain.reservation.service

import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repository.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationSearchCondition
import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
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
        logger.info("예약 요청 시작 - userId: $userId, seatId: $seatId")
        
        validateExistingReservation(seatId)
        
        val seat = seatService.getSeatById(seatId)
        reserveSeatStatus(seatId, userId)
        
        val reservation = createTemporaryReservation(userId, concertId, seatId, seat)
        
        publishReservationCreatedEvent(reservation)
        
        logger.info("예약 생성 성공 - reservationId: ${reservation.reservationId}, userId: $userId")
        return reservation
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun confirmReservation(reservationId: Long, paymentId: Long): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
            
        reservation.confirm(paymentId, statusRepository.getConfirmedStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        publishReservationConfirmedEvent(savedReservation, paymentId)
        return savedReservation
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun cancelReservation(reservationId: Long, userId: Long, cancelReason: String?): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
        
        if (reservation.userId != userId) {
            throw ReservationAccessDeniedException(userId, reservationId)
        }
        
        reservation.cancel(statusRepository.getCancelledStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        val event = ReservationCancelledEvent(
            reservationId = savedReservation.reservationId,
            userId = savedReservation.userId,
            concertId = savedReservation.concertId,
            seatId = savedReservation.seatId,
            cancelReason = cancelReason ?: "사용자 취소",
            isExpired = false
        )
        eventPublisher.publish(event)

        return savedReservation
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun cancelReservationBySystem(reservationId: Long, cancelReason: String): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
        
        reservation.cancel(statusRepository.getCancelledStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        val expiredEvent = ReservationExpiredEvent(
            reservationId = savedReservation.reservationId,
            userId = savedReservation.userId,
            concertId = savedReservation.concertId,
            seatId = savedReservation.seatId
        )
        eventPublisher.publish(expiredEvent)
        
        val cancelledEvent = ReservationCancelledEvent(
            reservationId = savedReservation.reservationId,
            userId = savedReservation.userId,
            concertId = savedReservation.concertId,
            seatId = savedReservation.seatId,
            cancelReason = cancelReason,
            isExpired = true
        )
        eventPublisher.publish(cancelledEvent)
        
        return savedReservation
    }
    
    fun getReservationWithLock(reservationId: Long): Reservation {
        return reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
    }
    
    fun getReservationById(reservationId: Long): Reservation {
        return reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
    }
    
    fun getReservationWithDetails(reservationId: Long): Reservation {
        return getReservationById(reservationId)
    }
    
    fun getReservationsByCondition(condition: ReservationSearchCondition): ReservationDto.Page {
        val pageable = PageRequest.of(
            condition.pageNumber - 1,
            condition.pageSize,
            if (condition.sortDirection.uppercase() == "DESC") 
                Sort.by(condition.sortBy).descending() 
            else 
                Sort.by(condition.sortBy).ascending()
        )
        
        val page = when {
            condition.userId != null && condition.statusList != null -> {
                reservationRepository.findByUserIdAndStatusCodeInOrderByReservedAtDesc(
                    condition.userId, condition.statusList, pageable
                )
            }
            condition.userId != null -> {
                reservationRepository.findByUserIdOrderByReservedAtDesc(condition.userId, pageable)
            }
            condition.concertId != null && condition.statusList != null -> {
                reservationRepository.findByConcertIdAndStatusCodeInOrderByReservedAtDesc(
                    condition.concertId, condition.statusList, pageable
                )
            }
            condition.concertId != null -> {
                reservationRepository.findByConcertIdOrderByReservedAtDesc(condition.concertId, pageable)
            }
            condition.statusList != null -> {
                reservationRepository.findByStatusCodeInOrderByReservedAtDesc(condition.statusList, pageable)
            }
            else -> {
                reservationRepository.findAll(pageable)
            }
        }
        
        return ReservationDto.Page.fromEntity(
            reservations = page.content,
            totalCount = page.totalElements.toInt(),
            pageNumber = condition.pageNumber,
            pageSize = condition.pageSize
        )
    }

    fun getExpiredReservations(pageNumber: Int, pageSize: Int): ReservationDto.Page {
        val expiredReservations = reservationRepository.findByExpiresAtBeforeAndStatusCode(
            LocalDateTime.now(), 
            statusRepository.getTemporaryStatus().code
        )
        
        val startIndex = (pageNumber - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, expiredReservations.size)
        val pageContent = if (startIndex < expiredReservations.size) {
            expiredReservations.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
        
        return ReservationDto.Page.fromEntity(
            reservations = pageContent,
            totalCount = expiredReservations.size,
            pageNumber = pageNumber,
            pageSize = pageSize
        )
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
                val lockedReservation = reservationRepository.findById(reservation.reservationId)
                
                if (lockedReservation != null && lockedReservation.isExpired()) {
                    cancelReservationBySystem(reservation.reservationId, "예약 시간 만료")
                    cleanedCount++
                }
            } catch (e: Exception) {
                logger.warn("Failed to cleanup expired reservation: ${reservation.reservationId}", e)
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
            logger.warn("기존 예약 존재 - reservationId: ${existingReservation.reservationId}, status: ${existingReservation.status.code}")
            
            if (existingReservation.isConfirmed()) {
                throw ReservationAlreadyConfirmedException(existingReservation.reservationId)
            }
            
            if (existingReservation.isTemporary() && !existingReservation.isExpired()) {
                throw SeatAlreadyReservedException(seatId)
            }
        }
    }
    
    private fun reserveSeatStatus(seatId: Long, userId: Long) {
        try {
            seatService.reserveSeat(seatId)
            logger.info("좌석 상태 변경 성공 - seatId: $seatId, userId: $userId")
        } catch (e: Exception) {
            logger.error("좌석 상태 변경 실패 - seatId: $seatId, userId: $userId", e)
            throw SeatAlreadyReservedException(seatId)
        }
    }
    
    private fun createTemporaryReservation(userId: Long, concertId: Long, seatId: Long, seat: Any): Reservation {
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
        val event = ReservationCreatedEvent(
            reservationId = reservation.reservationId,
            userId = reservation.userId,
            concertId = reservation.concertId,
            seatId = reservation.seatId,
            seatNumber = reservation.seatNumber,
            price = reservation.price,
            expiresAt = reservation.expiresAt
        )
        eventPublisher.publish(event)
    }
    
    private fun publishReservationConfirmedEvent(reservation: Reservation, paymentId: Long) {
        val event = ReservationConfirmedEvent(
            reservationId = reservation.reservationId,
            userId = reservation.userId,
            concertId = reservation.concertId,
            seatId = reservation.seatId,
            paymentId = paymentId,
            price = reservation.price
        )
        eventPublisher.publish(event)
    }
}
