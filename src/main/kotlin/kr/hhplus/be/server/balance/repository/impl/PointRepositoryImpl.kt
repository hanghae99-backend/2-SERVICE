package kr.hhplus.be.server.balance.repository.impl

import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.repository.PointRepository
import kr.hhplus.be.server.balance.repository.PointJpaRepository
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
