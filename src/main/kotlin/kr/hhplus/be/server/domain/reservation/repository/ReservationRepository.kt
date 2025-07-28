package kr.hhplus.be.server.domain.reservation.repository

import kr.hhplus.be.server.domain.reservation.model.Reservation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

interface ReservationRepository {
    fun save(reservation: Reservation): Reservation
    fun findById(id: Long): Reservation?
    fun findByUserIdOrderByReservedAtDesc(userId: Long): List<Reservation>
    fun findByUserIdOrderByReservedAtDesc(userId: Long, pageable: Pageable): Page<Reservation>
    fun findByConcertIdOrderByReservedAtDesc(concertId: Long): List<Reservation>
    fun findByConcertIdOrderByReservedAtDesc(concertId: Long, pageable: Pageable): Page<Reservation>
    fun findBySeatIdAndStatusCodeIn(seatId: Long, statusCodes: List<String>): Reservation?
    fun findByExpiresAtBeforeAndStatusCode(currentTime: LocalDateTime, statusCode: String): List<Reservation>
    fun findByStatusCodeInOrderByReservedAtDesc(statusCodes: List<String>, pageable: Pageable): Page<Reservation>
    fun findByUserIdAndStatusCodeInOrderByReservedAtDesc(userId: Long, statusCodes: List<String>, pageable: Pageable): Page<Reservation>
    fun findByConcertIdAndStatusCodeInOrderByReservedAtDesc(concertId: Long, statusCodes: List<String>, pageable: Pageable): Page<Reservation>
    fun findByReservedAtBetween(startDate: LocalDateTime, endDate: LocalDateTime): List<Reservation>
    fun findAll(): List<Reservation>
    fun findAll(pageable: Pageable): Page<Reservation>
    fun delete(reservation: Reservation)
}
