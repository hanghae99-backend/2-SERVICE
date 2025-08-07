package kr.hhplus.be.server.domain.payment.infrastructure

import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentStatusTypeJpaRepository : JpaRepository<PaymentStatusType, String> {
    fun findByCode(code: String): PaymentStatusType?
    fun findByIsActiveTrue(): List<PaymentStatusType>
    fun findByCodeAndIsActiveTrue(code: String): PaymentStatusType?
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
}
