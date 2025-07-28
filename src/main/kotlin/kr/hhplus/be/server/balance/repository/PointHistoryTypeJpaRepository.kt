package kr.hhplus.be.server.balance.repository

import kr.hhplus.be.server.balance.entity.PointHistoryType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryTypeJpaRepository : JpaRepository<PointHistoryType, String> {
    fun findByCode(code: String): PointHistoryType?
    fun findByIsActiveTrue(): List<PointHistoryType>
    fun findByCodeAndIsActiveTrue(code: String): PointHistoryType?
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
}
