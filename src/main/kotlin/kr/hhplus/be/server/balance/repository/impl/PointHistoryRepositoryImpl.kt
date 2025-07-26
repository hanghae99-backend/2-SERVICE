package kr.hhplus.be.server.balance.repository.impl

import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.balance.repository.PointHistoryRepository
import kr.hhplus.be.server.balance.repository.PointHistoryJpaRepository
import org.springframework.stereotype.Repository

@Repository
class PointHistoryRepositoryImpl(
    private val pointHistoryJpaRepository: PointHistoryJpaRepository
) : PointHistoryRepository {
    
    override fun save(pointHistory: PointHistory): PointHistory {
        return pointHistoryJpaRepository.save(pointHistory)
    }
    
    override fun findById(id: Long): PointHistory? {
        return pointHistoryJpaRepository.findById(id).orElse(null)
    }
    
    override fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory> {
        return pointHistoryJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
    
    override fun findAll(): List<PointHistory> {
        return pointHistoryJpaRepository.findAll()
    }
}
