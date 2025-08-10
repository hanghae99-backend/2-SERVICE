package kr.hhplus.be.server.domain.reservation.infrastructure

import kr.hhplus.be.server.domain.reservation.model.Reservation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface ReservationJpaRepository : JpaRepository<Reservation, Long> {
    
    @Query("SELECT r FROM Reservation r WHERE r.reservationId = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdWithPessimisticLock(@Param("id") id: Long): Optional<Reservation>
    
    // 사용자별 조회
    fun findByUserIdOrderByReservedAtDesc(userId: Long): List<Reservation>
    fun findByUserIdOrderByReservedAtDesc(userId: Long, pageable: Pageable): Page<Reservation>
    
    // 콘서트별 조회
    fun findByConcertIdOrderByReservedAtDesc(concertId: Long): List<Reservation>
    fun findByConcertIdOrderByReservedAtDesc(concertId: Long, pageable: Pageable): Page<Reservation>
    
    // 좌석 예약 확인 (비관적 락 사용)
    @Query("SELECT r FROM Reservation r WHERE r.seatId = :seatId AND r.status.code IN :statusCodes")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findBySeatIdAndStatusCodeIn(@Param("seatId") seatId: Long, @Param("statusCodes") statusCodes: List<String>): Reservation?
    
    // 만료된 예약 조회
    fun findByExpiresAtBeforeAndStatusCode(currentTime: LocalDateTime, statusCode: String): List<Reservation>
    
    // 상태별 조회 (페이징)
    fun findByStatusCodeInOrderByReservedAtDesc(statusCodes: List<String>, pageable: Pageable): Page<Reservation>
    
    // 사용자 + 상태별 조회 (페이징)
    fun findByUserIdAndStatusCodeInOrderByReservedAtDesc(userId: Long, statusCodes: List<String>, pageable: Pageable): Page<Reservation>
    
    // 콘서트 + 상태별 조회 (페이징)
    fun findByConcertIdAndStatusCodeInOrderByReservedAtDesc(concertId: Long, statusCodes: List<String>, pageable: Pageable): Page<Reservation>
    
    // 기간별 조회 (통계용)
    fun findByReservedAtBetween(startDate: LocalDateTime, endDate: LocalDateTime): List<Reservation>
}
