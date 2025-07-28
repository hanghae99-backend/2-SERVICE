package kr.hhplus.be.server.domain.balance.repositories

import kr.hhplus.be.server.domain.balance.models.PointHistoryType

interface PointHistoryTypePojoRepository {
    fun findByCode(code: String): PointHistoryType?
    fun findByIsActiveTrue(): List<PointHistoryType>
    fun findByCodeAndIsActiveTrue(code: String): PointHistoryType?
    fun findAll(): List<PointHistoryType>
    fun save(historyType: PointHistoryType): PointHistoryType
    fun deleteByCode(code: String)
    
    // ========== 상태 존재 여부 체크 ==========
    fun existsByCodeAndIsActiveTrue(code: String): Boolean
    
    // ========== 상태 조회 with 예외 처리 ==========
    fun getChargeType(): PointHistoryType
    fun getUseType(): PointHistoryType
    fun getTypeByCodeOrThrow(code: String): PointHistoryType
    
    // ========== 비즈니스 로직 메서드들 ==========
    fun getAllActiveTypes(): List<PointHistoryType>
    fun isValidType(code: String): Boolean
}
