package kr.hhplus.be.server.domain.reservation.service

import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repository.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationSearchCondition
import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationExpiredEvent
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val statusRepository: ReservationStatusTypePojoRepository,
    private val eventPublisher: DomainEventPublisher
) {
    
    @Transactional
    @LockGuard(key = "seat:#seatId")
    fun reserveSeat(userId: Long, concertId: Long, seatId: Long): Reservation {
        return reserveSeatInternal(userId, concertId, seatId)
    }

    @Transactional
    fun reserveSeatInternal(userId: Long, concertId: Long, seatId: Long): Reservation {
        val activeStatuses = listOf(
            statusRepository.getTemporaryStatus().code,
            statusRepository.getConfirmedStatus().code
        )
        val existingReservation = reservationRepository.findBySeatIdAndStatusCodeIn(seatId, activeStatuses)
        
        if (existingReservation != null) {
            if (existingReservation.isConfirmed()) {
                throw IllegalStateException("이미 확정된 좌석입니다")
            }
            if (existingReservation.isTemporary() && !existingReservation.isExpired()) {
                throw IllegalStateException("좌석이 임시 점유 중입니다")
            }
        }
        
        val reservation = Reservation.createTemporary(
            userId = userId,
            concertId = concertId,
            seatId = seatId,
            seatNumber = seatId.toString().padStart(2, '0'),
            price = BigDecimal("100000"),
            temporaryStatus = statusRepository.getTemporaryStatus()
        )
        
        val savedReservation = reservationRepository.save(reservation)
        
        val event = ReservationCreatedEvent(
            reservationId = savedReservation.reservationId,
            userId = userId,
            concertId = concertId,
            seatId = seatId,
            seatNumber = savedReservation.seatNumber,
            price = savedReservation.price,
            expiresAt = savedReservation.expiresAt
        )
        eventPublisher.publish(event)
        
        return savedReservation
    }
    
    @Transactional
    @LockGuard(key = "reservation:#reservationId")
    fun confirmReservation(reservationId: Long, paymentId: Long): Reservation {
        return confirmReservationInternal(reservationId, paymentId)
    }
    
    @Transactional
    fun confirmReservationInternal(reservationId: Long, paymentId: Long): Reservation {
        val reservation = getReservationById(reservationId)
        reservation.confirm(paymentId, statusRepository.getConfirmedStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        val event = ReservationConfirmedEvent(
            reservationId = savedReservation.reservationId,
            userId = savedReservation.userId,
            concertId = savedReservation.concertId,
            seatId = savedReservation.seatId,
            paymentId = paymentId,
            price = savedReservation.price
        )
        eventPublisher.publish(event)
        
        return savedReservation
    }
    
    @Transactional
    @LockGuard(key = "reservation:#reservationId")
    fun cancelReservation(reservationId: Long, userId: Long, cancelReason: String?): Reservation {
        return cancelReservationInternal(reservationId, userId, cancelReason)
    }
    
    private fun cancelReservationInternal(reservationId: Long, userId: Long, cancelReason: String?): Reservation {
        val reservation = getReservationById(reservationId)
        
        if (reservation.userId != userId) {
            throw IllegalArgumentException("본인의 예약만 취소할 수 있습니다")
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
    
    fun getReservationById(reservationId: Long): Reservation {
        return reservationRepository.findById(reservationId)
            .orElseThrow { IllegalArgumentException("예약을 찾을 수 없습니다: $reservationId") }
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
    
    @Transactional
    fun cleanupExpiredReservations(): Int {
        val expiredReservations = reservationRepository.findByExpiresAtBeforeAndStatusCode(
            LocalDateTime.now(),
            statusRepository.getTemporaryStatus().code
        )
        
        expiredReservations.forEach { reservation ->
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
                cancelReason = "예약 시간 만료",
                isExpired = true
            )
            eventPublisher.publish(cancelledEvent)
        }
        
        return expiredReservations.size
    }
}
