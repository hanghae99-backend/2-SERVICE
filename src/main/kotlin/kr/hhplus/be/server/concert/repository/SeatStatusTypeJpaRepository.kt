package kr.hhplus.be.server.concert.repository

import kr.hhplus.be.server.concert.entity.SeatStatusType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SeatStatusTypeJpaRepository : JpaRepository<SeatStatusType, String> {
    fun findByCode(code: String): SeatStatusType?
    fun findByIsActiveTrue(): List<SeatStatusType>
    fun findByCodeAndIsActiveTrue(code: String): SeatStatusType?
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
}
