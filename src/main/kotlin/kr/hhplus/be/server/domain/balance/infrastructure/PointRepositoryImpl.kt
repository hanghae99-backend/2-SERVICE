package kr.hhplus.be.server.domain.balance.infrastructure

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import org.springframework.stereotype.Repository

@Repository
class PointRepositoryImpl(
    private val pointJpaRepository: PointJpaRepository
) : PointRepository {
    
    override fun save(point: Point): Point {
        return pointJpaRepository.save(point)
    }
    
    override fun findById(id: Long): Point? {
        return pointJpaRepository.findById(id).orElse(null)
    }
    
    override fun findByUserId(userId: Long): Point? {
        return pointJpaRepository.findByUserId(userId)
    }
    
    override fun delete(point: Point) {
        pointJpaRepository.delete(point)
    }
}
