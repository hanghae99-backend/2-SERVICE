package kr.hhplus.be.server.payment.repository.impl

import kr.hhplus.be.server.payment.entity.PaymentStatusType
import kr.hhplus.be.server.payment.repository.PaymentStatusTypeJpaRepository
import kr.hhplus.be.server.payment.repository.PaymentStatusTypePojoRepository
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
        return findByCodeAndIsActiveTrue("PENDING")
            ?: throw IllegalStateException("PENDING 상태를 찾을 수 없습니다")
    }
    
    override fun getCompletedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("COMPLETED")
            ?: throw IllegalStateException("COMPLETED 상태를 찾을 수 없습니다")
    }
    
    override fun getFailedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("FAILED")
            ?: throw IllegalStateException("FAILED 상태를 찾을 수 없습니다")
    }
    
    override fun getCancelledStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("CANCELLED")
            ?: throw IllegalStateException("CANCELLED 상태를 찾을 수 없습니다")
    }
    
    override fun getRefundedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue("REFUNDED")
            ?: throw IllegalStateException("REFUNDED 상태를 찾을 수 없습니다")
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
