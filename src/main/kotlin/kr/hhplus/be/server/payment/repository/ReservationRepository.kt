package kr.hhplus.be.server.payment.repository

import kr.hhplus.be.server.payment.entity.Reservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ReservationRepository : JpaRepository<Reservation, Long> {
    
    @Query("SELECT r FROM Reservation r WHERE r.userId = :userId ORDER BY r.createdAt DESC")
    fun findByUserIdOrderByCreatedAtDesc(@Param("userId") userId: Long): List<Reservation>
    
    @Query("SELECT r FROM Reservation r WHERE r.concertId = :concertId ORDER BY r.createdAt DESC")
    fun findByConcertIdOrderByCreatedAtDesc(@Param("concertId") concertId: Long): List<Reservation>
    
    @Query("SELECT r FROM Reservation r WHERE r.userId = :userId AND r.statusCode = :statusCode ORDER BY r.createdAt DESC")
    fun findByUserIdAndStatusOrderByCreatedAtDesc(@Param("userId") userId: Long, @Param("statusCode") statusCode: String): List<Reservation>
    
    @Query("SELECT r FROM Reservation r WHERE r.seatId = :seatId AND r.statusCode = :statusCode")
    fun findBySeatIdAndStatus(@Param("seatId") seatId: Long, @Param("statusCode") statusCode: String): Reservation?
    
    @Query("SELECT r FROM Reservation r WHERE r.expiresAt < CURRENT_TIMESTAMP AND r.statusCode = :statusCode")
    fun findExpiredReservations(@Param("statusCode") statusCode: String): List<Reservation>
    
    @Query("SELECT r FROM Reservation r WHERE r.expiresAt < :currentTime AND r.statusCode = 'TEMPORARY'")
    fun findExpiredTemporaryReservations(@Param("currentTime") currentTime: LocalDateTime): List<Reservation>
    
    fun findByReservationId(reservationId: Long): Reservation?
}
