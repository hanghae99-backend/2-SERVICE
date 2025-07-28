package kr.hhplus.be.server.domain.balance.infrastructure

import kr.hhplus.be.server.domain.balance.models.PointHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryJpaRepository : JpaRepository<PointHistory, Long> {
    
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory>
}
