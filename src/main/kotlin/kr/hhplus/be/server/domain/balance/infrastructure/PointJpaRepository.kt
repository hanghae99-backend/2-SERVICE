package kr.hhplus.be.server.domain.balance.infrastructure

import kr.hhplus.be.server.domain.balance.models.Point
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType
import java.util.Optional

@Repository
interface PointJpaRepository : JpaRepository<Point, Long> {
    
    fun findByUserId(userId: Long): Point?
    
    @Query("SELECT p FROM Point p WHERE p.userId = :userId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserIdWithPessimisticLock(@Param("userId") userId: Long): Optional<Point>
}
