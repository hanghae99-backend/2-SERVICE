package kr.hhplus.be.server.domain.payment.infrastructure

import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.global.constants.CacheConstants
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
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

    @Cacheable(value = [CacheConstants.PAYMENT_STATUS_TYPES], key = "'all'")
    override fun findAll(): List<PaymentStatusType> {
        return jpaRepository.findAll()
    }

    @CacheEvict(value = [CacheConstants.PAYMENT_STATUS_TYPES], allEntries = true)
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
    @Cacheable(value = [CacheConstants.PAYMENT_STATUS_TYPES], key = "'exists:' + #code")
    override fun existsByCodeAndIsActiveTrue(code: String): Boolean {
        return jpaRepository.existsByCodeAndIsActiveTrue(code)
    }
    
    // ========== 상태 조회 with 예외 처리 ==========
    @Cacheable(value = [CacheConstants.PAYMENT_STATUS_TYPES], key = "'status:PENDING'")
    override fun getPendingStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue(PaymentStatusType.PENDING)
            ?: throw IllegalStateException("PENDING 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = [CacheConstants.PAYMENT_STATUS_TYPES], key = "'status:COMPLETED'")
    override fun getCompletedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue(PaymentStatusType.COMPLETED)
            ?: throw IllegalStateException("COMPLETED 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = [CacheConstants.PAYMENT_STATUS_TYPES], key = "'status:FAILED'")
    override fun getFailedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue(PaymentStatusType.FAILED)
            ?: throw IllegalStateException("FAILED 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = [CacheConstants.PAYMENT_STATUS_TYPES], key = "'status:CANCELLED'")
    override fun getCancelledStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue(PaymentStatusType.CANCELLED)
            ?: throw IllegalStateException("CANCELLED 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = [CacheConstants.PAYMENT_STATUS_TYPES], key = "'status:REFUNDED'")
    override fun getRefundedStatus(): PaymentStatusType {
        return findByCodeAndIsActiveTrue(PaymentStatusType.REFUNDED)
            ?: throw IllegalStateException("REFUNDED 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = [CacheConstants.PAYMENT_STATUS_TYPES], key = "'status:' + #code")
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
    
    // ========== JPA 연동 메서드 ==========
    override fun flush() {
        jpaRepository.flush()
    }
}
