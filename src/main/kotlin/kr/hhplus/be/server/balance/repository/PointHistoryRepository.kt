package kr.hhplus.be.server.balance.repository

import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.balance.entity.PointHistoryType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryRepository : JpaRepository<PointHistory, Long> {
    
    @Query("SELECT ph FROM PointHistory ph WHERE ph.userId = :userId ORDER BY ph.createdAt DESC")
    fun findByUserIdOrderByCreatedAtDesc(@Param("userId") userId: Long): List<PointHistory>
    
    @Query("SELECT ph FROM PointHistory ph WHERE ph.userId = :userId AND ph.type = :type ORDER BY ph.createdAt DESC")
    fun findByUserIdAndTypeOrderByCreatedAtDesc(@Param("userId") userId: Long, @Param("type") type: PointHistoryType): List<PointHistory>
}
