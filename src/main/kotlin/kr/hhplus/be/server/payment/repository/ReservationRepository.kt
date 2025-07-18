package kr.hhplus.be.server.payment.repository

import kr.hhplus.be.server.payment.entity.Reservation
import kr.hhplus.be.server.payment.entity.ReservationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ReservationRepository : JpaRepository<Reservation, Long> {
    
    @Query("SELECT r FROM Reservation r WHERE r.userId = :userId ORDER BY r.createdAt DESC")
    fun findByUserIdOrderByCreatedAtDesc(@Param("userId") userId: Long): List<Reservation>
    
    @Query("SELECT r FROM Reservation r WHERE r.userId = :userId AND r.status = :status ORDER BY r.createdAt DESC")
    fun findByUserIdAndStatusOrderByCreatedAtDesc(@Param("userId") userId: Long, @Param("status") status: ReservationStatus): List<Reservation>
    
    @Query("SELECT r FROM Reservation r WHERE r.seatId = :seatId AND r.status = :status")
    fun findBySeatIdAndStatus(@Param("seatId") seatId: Long, @Param("status") status: ReservationStatus): Reservation?
    
    @Query("SELECT r FROM Reservation r WHERE r.expiresAt < CURRENT_TIMESTAMP AND r.status = :status")
    fun findExpiredReservations(@Param("status") status: ReservationStatus): List<Reservation>
    
    @Query("SELECT r FROM Reservation r WHERE r.expiresAt < :currentTime AND r.status = 'TEMPORARY'")
    fun findExpiredTemporaryReservations(@Param("currentTime") currentTime: LocalDateTime): List<Reservation>
    
    fun findByReservationId(reservationId: Long): Reservation?
}
