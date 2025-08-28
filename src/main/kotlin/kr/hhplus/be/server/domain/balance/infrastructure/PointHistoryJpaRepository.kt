package kr.hhplus.be.server.domain.balance.infrastructure

import kr.hhplus.be.server.domain.balance.models.PointHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
interface PointHistoryJpaRepository : JpaRepository<PointHistory, Long> {
    
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory>
    
    @Query("""
        SELECT COALESCE(SUM(ph.amount), 0) 
        FROM PointHistory ph 
        WHERE ph.userId = :userId 
        AND ph.historyType.code = 'CHARGE'
        AND ph.createdAt >= :startOfDay
        AND ph.createdAt < :endOfDay
    """)
    fun sumChargeAmountByUserIdAndDate(
        @Param("userId") userId: Long, 
        @Param("startOfDay") startOfDay: LocalDateTime,
        @Param("endOfDay") endOfDay: LocalDateTime
    ): BigDecimal
}
