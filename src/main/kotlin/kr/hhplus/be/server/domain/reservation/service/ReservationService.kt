package kr.hhplus.be.server.domain.reservation.service

import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockKeyManager
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
    private val distributedLock: DistributedLock,
    private val eventPublisher: DomainEventPublisher
) {
        
        // ========== 비즈니스 메서드들 ==========
    
    @Transactional
    fun reserveSeat(userId: Long, concertId: Long, seatId: Long): Reservation {
        val lockKey = LockKeyManager.seatOperation(seatId)
        
        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 10000L,  // 10초 락 타임아웃
            waitTimeoutMs = 5000L    // 5초 대기 타임아웃
        ) {
            reserveSeatInternal(userId, concertId, seatId)
        }
    }

    @Transactional
    fun reserveSeatInternal(userId: Long, concertId: Long, seatId: Long): Reservation {
        // 기존 활성 예약 확인
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
        
        // 예약 생성
        val reservation = Reservation.createTemporary(
            userId = userId,
            concertId = concertId,
            seatId = seatId,
            seatNumber = seatId.toString().padStart(2, '0'), // 01, 02 형식
            price = BigDecimal("100000"), // 10만원 통일
            temporaryStatus = statusRepository.getTemporaryStatus()
        )
        
        val savedReservation = reservationRepository.save(reservation)
        
        // 예약 생성 이벤트 발행
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
    fun confirmReservation(reservationId: Long, paymentId: Long): Reservation {
        val lockKey = LockKeyManager.reservationConfirm(reservationId)
        
        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 10000L,
            waitTimeoutMs = 5000L
        ) {
            confirmReservationInternal(reservationId, paymentId)
        }
    }
    
    /**
     * PaymentService에서 내부 호출용 (중첩 락 방지)
     */
    @Transactional
    fun confirmReservationInternal(reservationId: Long, paymentId: Long): Reservation {
        val reservation = getReservationById(reservationId)
        reservation.confirm(paymentId, statusRepository.getConfirmedStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        // 예약 확정 이벤트 발행
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
    fun cancelReservation(reservationId: Long, userId: Long, cancelReason: String?): Reservation {
        val lockKey = LockKeyManager.reservationCancel(reservationId)
        
        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 10000L,
            waitTimeoutMs = 5000L
        ) {
            cancelReservationInternal(reservationId, userId, cancelReason)
        }
    }
    
    private fun cancelReservationInternal(reservationId: Long, userId: Long, cancelReason: String?): Reservation {
        val reservation = getReservationById(reservationId)
        
        if (reservation.userId != userId) {
            throw IllegalArgumentException("본인의 예약만 취소할 수 있습니다")
        }
        
        reservation.cancel(statusRepository.getCancelledStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        // 예약 취소 이벤트 발행
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
            
            // 예약 만료 이벤트 발행
            val expiredEvent = ReservationExpiredEvent(
                reservationId = savedReservation.reservationId,
                userId = savedReservation.userId,
                concertId = savedReservation.concertId,
                seatId = savedReservation.seatId
            )
            eventPublisher.publish(expiredEvent)
            
            // 예약 취소 이벤트도 발행 (만료로 인한 취소)
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
