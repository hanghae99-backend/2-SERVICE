package kr.hhplus.be.server.balance.repository

import kr.hhplus.be.server.balance.entity.PointHistory

interface PointHistoryRepository {
    fun save(pointHistory: PointHistory): PointHistory
    fun findById(id: Long): PointHistory?
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory>
    fun findAll(): List<PointHistory>
}
