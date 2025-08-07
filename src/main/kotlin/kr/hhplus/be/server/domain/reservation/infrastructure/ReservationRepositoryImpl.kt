package kr.hhplus.be.server.domain.reservation.infrastructure

import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ReservationRepositoryImpl(
    private val reservationJpaRepository: ReservationJpaRepository
) : ReservationRepository {
    
    override fun save(reservation: Reservation): Reservation {
        return reservationJpaRepository.save(reservation)
    }
    
    override fun findById(id: Long): Reservation? {
        return reservationJpaRepository.findById(id).orElse(null)
    }
    
    override fun findByIdWithPessimisticLock(id: Long): Reservation? {
        return reservationJpaRepository.findByIdWithPessimisticLock(id).orElse(null)
    }
    
    override fun findByUserIdOrderByReservedAtDesc(userId: Long): List<Reservation> {
        return reservationJpaRepository.findByUserIdOrderByReservedAtDesc(userId)
    }
    
    override fun findByUserIdOrderByReservedAtDesc(userId: Long, pageable: Pageable): Page<Reservation> {
        return reservationJpaRepository.findByUserIdOrderByReservedAtDesc(userId, pageable)
    }
    
    override fun findByConcertIdOrderByReservedAtDesc(concertId: Long): List<Reservation> {
        return reservationJpaRepository.findByConcertIdOrderByReservedAtDesc(concertId)
    }
    
    override fun findByConcertIdOrderByReservedAtDesc(concertId: Long, pageable: Pageable): Page<Reservation> {
        return reservationJpaRepository.findByConcertIdOrderByReservedAtDesc(concertId, pageable)
    }
    
    override fun findBySeatIdAndStatusCodeIn(seatId: Long, statusCodes: List<String>): Reservation? {
        return reservationJpaRepository.findBySeatIdAndStatusCodeIn(seatId, statusCodes)
    }
    
    override fun findByExpiresAtBeforeAndStatusCode(currentTime: LocalDateTime, statusCode: String): List<Reservation> {
        return reservationJpaRepository.findByExpiresAtBeforeAndStatusCode(currentTime, statusCode)
    }
    
    override fun findByStatusCodeInOrderByReservedAtDesc(statusCodes: List<String>, pageable: Pageable): Page<Reservation> {
        return reservationJpaRepository.findByStatusCodeInOrderByReservedAtDesc(statusCodes, pageable)
    }
    
    override fun findByUserIdAndStatusCodeInOrderByReservedAtDesc(userId: Long, statusCodes: List<String>, pageable: Pageable): Page<Reservation> {
        return reservationJpaRepository.findByUserIdAndStatusCodeInOrderByReservedAtDesc(userId, statusCodes, pageable)
    }
    
    override fun findByConcertIdAndStatusCodeInOrderByReservedAtDesc(concertId: Long, statusCodes: List<String>, pageable: Pageable): Page<Reservation> {
        return reservationJpaRepository.findByConcertIdAndStatusCodeInOrderByReservedAtDesc(concertId, statusCodes, pageable)
    }
    
    override fun findByReservedAtBetween(startDate: LocalDateTime, endDate: LocalDateTime): List<Reservation> {
        return reservationJpaRepository.findByReservedAtBetween(startDate, endDate)
    }
    
    override fun findAll(pageable: Pageable): Page<Reservation> {
        return reservationJpaRepository.findAll(pageable)
    }
    
    override fun findAll(): List<Reservation> {
        return reservationJpaRepository.findAll()
    }

    override fun delete(reservation: Reservation) {
        reservationJpaRepository.delete(reservation)
    }
    
    override fun deleteAll() {
        reservationJpaRepository.deleteAll()
    }
}
