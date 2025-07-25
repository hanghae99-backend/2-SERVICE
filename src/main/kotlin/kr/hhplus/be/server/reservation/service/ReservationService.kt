package kr.hhplus.be.server.reservation.service

import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.reservation.entity.Reservation
import kr.hhplus.be.server.reservation.entity.ReservationStatusType
import kr.hhplus.be.server.reservation.repository.ReservationRepository
import kr.hhplus.be.server.reservation.dto.ReservationDto
import kr.hhplus.be.server.reservation.dto.request.ReservationSearchCondition
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ReservationService(
    private val reservationRepository: ReservationRepository
) {
    
    @Transactional
    fun reserveSeat(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        // 기존 활성 예약 확인
        val activeStatuses = listOf(ReservationStatusType.TEMPORARY, ReservationStatusType.CONFIRMED)
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
            seatNumber = "A${seatId}", // 임시 좌석 번호
            price = BigDecimal("50000") // 임시 가격
        )
        
        return reservationRepository.save(reservation)
    }
    
    @Transactional
    fun confirmReservation(reservationId: Long, paymentId: Long): Reservation {
        val reservation = getReservationById(reservationId)
        reservation.confirm(paymentId)
        return reservationRepository.save(reservation)
    }
    
    @Transactional
    fun cancelReservation(reservationId: Long, userId: Long, cancelReason: String?): Reservation {
        val reservation = getReservationById(reservationId)
        
        if (reservation.userId != userId) {
            throw IllegalArgumentException("본인의 예약만 취소할 수 있습니다")
        }
        
        reservation.cancel()
        return reservationRepository.save(reservation)
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
            ReservationStatusType.TEMPORARY
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
            ReservationStatusType.TEMPORARY
        )
        
        expiredReservations.forEach { reservation ->
            reservation.cancel()
            reservationRepository.save(reservation)
        }
        
        return expiredReservations.size
    }
}
