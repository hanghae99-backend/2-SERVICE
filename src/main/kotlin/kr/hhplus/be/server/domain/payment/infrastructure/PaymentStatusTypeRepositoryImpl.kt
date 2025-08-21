package kr.hhplus.be.server.domain.payment.infrastructure

import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentStatusTypeRepositoryImpl(
    private val jpaRepository: PaymentStatusTypeJpaRepository
) : PaymentStatusTypePojoRepository {

    override fun findByCode(code: String): PaymentStatusType? {
        return jpaRepository.findByCode(code)
    }

    override fun findByIsActiveTrue(): List<PaymentStatusType> {
        return jpaRepository.findByIsActiveTrue()
    }

    override fun findByCodeAndIsActiveTrue(code: String): PaymentStatusType? {
        return jpaRepository.findByCodeAndIsActiveTrue(code)
    }

    override fun findAll(): List<PaymentStatusType> {
        return jpaRepository.findAll()
    }

    override fun save(statusType: PaymentStatusType): PaymentStatusType {
        return jpaRepository.save(statusType)
    }

    override fun deleteByCode(code: String) {
        val statusType = findByCode(code)
        if (statusType != null) {
            jpaRepository.delete(statusType)
        }
    }
    
    // ========== 상태 존재 여부 체크 ==========
    override fun existsByCodeAndIsActiveTrue(code: String): Boolean {
        return jpaRepository.existsByCodeAndIsActiveTrue(code)
    }
    
    // ========== 상태 조회 with 예외 처리 ==========
    override fun getPendingStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("PEND")
            ?: throw IllegalStateException("PEND 상태를 찾을 수 없습니다")
    }
    
    override fun getCompletedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("COMP")
            ?: throw IllegalStateException("COMP 상태를 찾을 수 없습니다")
    }
    
    override fun getFailedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("FAIL")
            ?: throw IllegalStateException("FAIL 상태를 찾을 수 없습니다")
    }
    
    override fun getCancelledStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("CANC")
            ?: throw IllegalStateException("CANC 상태를 찾을 수 없습니다")
    }
    
    override fun getRefundedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("REFD")
            ?: throw IllegalStateException("REFD 상태를 찾을 수 없습니다")
    }
    
    override fun getStatusByCodeOrThrow(code: String): PaymentStatusType {
        return findByCodeAndIsActiveTrue(code)
            ?: throw IllegalStateException("$code 상태를 찾을 수 없습니다")
    }
    
    // ========== 비즈니스 로직 메서드들 ==========
    override fun getAllActiveStatuses(): List<PaymentStatusType> {
        return findByIsActiveTrue()
    }
    
    override fun isValidStatus(code: String): Boolean {
        return existsByCodeAndIsActiveTrue(code)
    }
}
