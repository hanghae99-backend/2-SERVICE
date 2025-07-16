package kr.hhplus.be.server.concert.repository

import kr.hhplus.be.server.concert.entity.Concert
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ConcertJpaRepository : JpaRepository<Concert, Long> {
    
    fun findByConcertDate(concertDate: LocalDate): List<Concert>
    
    fun findByConcertDateBetween(startDate: LocalDate, endDate: LocalDate): List<Concert>
    
    @Query("""
        SELECT c FROM Concert c
        WHERE c.concertDate BETWEEN :startDate AND :endDate
        AND EXISTS (
            SELECT 1 FROM Seat s 
            WHERE s.concertId = c.concertId 
            AND s.status = 'AVAILABLE'
        )
        ORDER BY c.concertDate ASC, c.startTime ASC
    """)
    fun findAvailableConcertsByDateRange(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<Concert>
    
    @Query("""
        SELECT COUNT(s) FROM Seat s 
        WHERE s.concertId = :concertId 
        AND s.status = 'AVAILABLE'
    """)
    fun countAvailableSeatsByConcertId(@Param("concertId") concertId: Long): Int
}
