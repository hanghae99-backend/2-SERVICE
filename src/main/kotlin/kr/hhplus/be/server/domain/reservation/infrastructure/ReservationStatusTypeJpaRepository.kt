package kr.hhplus.be.server.domain.reservation.infrastructure

import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReservationStatusTypeJpaRepository : JpaRepository<ReservationStatusType, String> {
    fun findByCode(code: String): ReservationStatusType?
    fun findByIsActiveTrue(): List<ReservationStatusType>
    fun findByCodeAndIsActiveTrue(code: String): ReservationStatusType?
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
}
