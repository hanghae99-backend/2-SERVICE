package kr.hhplus.be.server.balance.repository

import kr.hhplus.be.server.balance.entity.Point
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointRepository : JpaRepository<Point, Long> {
    
    fun findByUserId(userId: Long): Point?
}
