package kr.hhplus.be.server.reservation.repository

import kr.hhplus.be.server.reservation.entity.Reservation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ReservationRepository : JpaRepository<Reservation, Long> {
    
    // 사용자별 조회
    fun findByUserIdOrderByReservedAtDesc(userId: Long): List<Reservation>
    fun findByUserIdOrderByReservedAtDesc(userId: Long, pageable: Pageable): Page<Reservation>
    
    // 콘서트별 조회
    fun findByConcertIdOrderByReservedAtDesc(concertId: Long): List<Reservation>
    fun findByConcertIdOrderByReservedAtDesc(concertId: Long, pageable: Pageable): Page<Reservation>
    
    // 좌석 예약 확인
    fun findBySeatIdAndStatusCodeIn(seatId: Long, statusCodes: List<String>): Reservation?
    
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
