package kr.hhplus.be.server.domain.balance.infrastructure

import kr.hhplus.be.server.domain.balance.models.Point
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointJpaRepository : JpaRepository<Point, Long> {
    
    fun findByUserId(userId: Long): Point?
}
