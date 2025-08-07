package kr.hhplus.be.server.domain.payment.repository

import kr.hhplus.be.server.domain.payment.models.PaymentStatusType

interface PaymentStatusTypePojoRepository {
    fun findByCode(code: String): PaymentStatusType?
    fun findByIsActiveTrue(): List<PaymentStatusType>
    fun findByCodeAndIsActiveTrue(code: String): PaymentStatusType?
    fun findAll(): List<PaymentStatusType>
    fun save(statusType: PaymentStatusType): PaymentStatusType
    fun deleteByCode(code: String)
    
    // ========== 상태 존재 여부 체크 ==========
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
    
    // ========== 상태 조회 with 예외 처리 ==========
    fun getPendingStatus(): PaymentStatusType
    fun getCompletedStatus(): PaymentStatusType
    fun getFailedStatus(): PaymentStatusType
    fun getCancelledStatus(): PaymentStatusType
    fun getRefundedStatus(): PaymentStatusType
    fun getStatusByCodeOrThrow(code: String): PaymentStatusType
    
    // ========== 비즈니스 로직 메서드들 ==========
    fun getAllActiveStatuses(): List<PaymentStatusType>
    fun isValidStatus(code: String): Boolean
}
