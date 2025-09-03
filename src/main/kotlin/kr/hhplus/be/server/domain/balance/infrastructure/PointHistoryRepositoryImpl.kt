package kr.hhplus.be.server.domain.balance.infrastructure

import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

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
    
    override fun deleteAll() {
        pointHistoryJpaRepository.deleteAll()
    }
    
    override fun findChargeAmountByUserIdAndDate(userId: Long, date: LocalDate): BigDecimal? {
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.plusDays(1).atStartOfDay()
        return pointHistoryJpaRepository.sumChargeAmountByUserIdAndDate(userId, startOfDay, endOfDay)
    }
    
    override fun flush() {
        pointHistoryJpaRepository.flush()
    }
}
