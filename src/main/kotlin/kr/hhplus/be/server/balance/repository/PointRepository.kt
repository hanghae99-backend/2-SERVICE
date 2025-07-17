package kr.hhplus.be.server.balance.repository

import kr.hhplus.be.server.balance.entity.Point
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PointRepository : JpaRepository<Point, Long> {
    
    @Query("SELECT p FROM Point p WHERE p.userId = :userId")
    fun findByUserId(@Param("userId") userId: Long): Point?
    
    fun existsByUserId(userId: Long): Boolean
}
