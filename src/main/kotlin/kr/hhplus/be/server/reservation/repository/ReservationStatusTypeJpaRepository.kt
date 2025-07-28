package kr.hhplus.be.server.reservation.repository

import kr.hhplus.be.server.reservation.entity.ReservationStatusType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReservationStatusTypeJpaRepository : JpaRepository<ReservationStatusType, String> {
    fun findByCode(code: String): ReservationStatusType?
    fun findByIsActiveTrue(): List<ReservationStatusType>
    fun findByCodeAndIsActiveTrue(code: String): ReservationStatusType?
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
}
