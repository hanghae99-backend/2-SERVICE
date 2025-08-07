package kr.hhplus.be.server.domain.concert.repositories

import kr.hhplus.be.server.domain.concert.models.SeatStatusType

interface SeatStatusTypePojoRepository {
    fun findByCode(code: String): SeatStatusType?
    fun findByIsActiveTrue(): List<SeatStatusType>
    fun findByCodeAndIsActiveTrue(code: String): SeatStatusType?
    fun findAll(): List<SeatStatusType>
    fun save(statusType: SeatStatusType): SeatStatusType
    fun deleteByCode(code: String)
    
    // ========== 상태 존재 여부 체크 ==========
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
    
    // ========== 상태 조회 with 예외 처리 ==========
    fun getAvailableStatus(): SeatStatusType
    fun getReservedStatus(): SeatStatusType
    fun getOccupiedStatus(): SeatStatusType
    fun getMaintenanceStatus(): SeatStatusType
    fun getStatusByCodeOrThrow(code: String): SeatStatusType
    
    // ========== 비즈니스 로직 메서드들 ==========
    fun getAllActiveStatuses(): List<SeatStatusType>
    fun isValidStatus(code: String): Boolean
}
