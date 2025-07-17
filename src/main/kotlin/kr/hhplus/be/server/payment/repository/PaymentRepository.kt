package kr.hhplus.be.server.payment.repository

import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.payment.entity.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {
    
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId ORDER BY p.createdAt DESC")
    fun findByUserIdOrderByCreatedAtDesc(@Param("userId") userId: Long): List<Payment>
    
    @Query("SELECT p FROM Payment p WHERE p.reservationId = :reservationId")
    fun findByReservationId(@Param("reservationId") reservationId: Long): Payment?
    
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status = :status ORDER BY p.createdAt DESC")
    fun findByUserIdAndStatusOrderByCreatedAtDesc(@Param("userId") userId: Long, @Param("status") status: PaymentStatus): List<Payment>
    
    fun existsByReservationId(reservationId: Long): Boolean
}
