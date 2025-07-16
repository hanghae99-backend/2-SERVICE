package kr.hhplus.be.server.concert.repository

import kr.hhplus.be.server.concert.entity.Seat
import kr.hhplus.be.server.concert.entity.SeatStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface SeatJpaRepository : JpaRepository<Seat, Long> {
    
    fun findByConcertId(concertId: Long): List<Seat>
    
    fun findByConcertIdAndStatus(concertId: Long, status: SeatStatus): List<Seat>
    
    fun findByConcertIdAndSeatNumber(concertId: Long, seatNumber: Int): Seat?
    
    @Query("""
        SELECT s FROM Seat s 
        WHERE s.concertId = :concertId 
        AND s.status = 'AVAILABLE'
        ORDER BY s.seatNumber ASC
    """)
    fun findAvailableSeatsByConcertId(@Param("concertId") concertId: Long): List<Seat>
    
    
    @Query("""
        SELECT COUNT(s) FROM Seat s 
        WHERE s.concertId = :concertId 
        AND s.status = 'AVAILABLE'
    """)
    fun countAvailableSeatsByConcertId(@Param("concertId") concertId: Long): Int
    
    @Modifying
    @Transactional
    @Query("""
        UPDATE Seat s 
        SET s.status = :status, s.updatedAt = CURRENT_TIMESTAMP 
        WHERE s.seatId = :seatId
    """)
    fun updateSeatStatus(@Param("seatId") seatId: Long, @Param("status") status: SeatStatus): Int
}
