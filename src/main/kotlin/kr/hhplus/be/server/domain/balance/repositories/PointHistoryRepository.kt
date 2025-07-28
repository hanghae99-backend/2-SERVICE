package kr.hhplus.be.server.domain.balance.repositories

import kr.hhplus.be.server.domain.balance.models.PointHistory

interface PointHistoryRepository {
    fun save(pointHistory: PointHistory): PointHistory
    fun findById(id: Long): PointHistory?
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory>
    fun findAll(): List<PointHistory>
}
