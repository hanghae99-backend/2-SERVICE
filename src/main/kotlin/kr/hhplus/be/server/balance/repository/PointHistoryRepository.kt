package kr.hhplus.be.server.balance.repository

import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.balance.entity.PointHistoryType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryRepository : JpaRepository<PointHistory, Long> {
    
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory>
}
