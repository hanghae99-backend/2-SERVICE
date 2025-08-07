package kr.hhplus.be.server.domain.reservation.repository

import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType


interface ReservationStatusTypePojoRepository {
    fun findByCode(code: String): ReservationStatusType?
    fun findByIsActiveTrue(): List<ReservationStatusType>
    fun findByCodeAndIsActiveTrue(code: String): ReservationStatusType?
    fun findAll(): List<ReservationStatusType>
    fun save(statusType: ReservationStatusType): ReservationStatusType
    fun deleteByCode(code: String)
    
    // ========== 상태 존재 여부 체크 ==========
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
    
    // ========== 상태 조회 with 예외 처리 ==========
    fun getTemporaryStatus(): ReservationStatusType
    fun getConfirmedStatus(): ReservationStatusType
    fun getCancelledStatus(): ReservationStatusType
    fun getStatusByCodeOrThrow(code: String): ReservationStatusType
    
    // ========== 비즈니스 로직 메서드들 ==========
    fun getAllActiveStatuses(): List<ReservationStatusType>
    fun isValidStatus(code: String): Boolean
}
