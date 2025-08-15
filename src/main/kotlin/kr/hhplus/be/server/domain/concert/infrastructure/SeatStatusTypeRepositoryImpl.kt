package kr.hhplus.be.server.domain.concert.infrastructure

import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Repository

@Repository
class SeatStatusTypeRepositoryImpl(
    private val jpaRepository: SeatStatusTypeJpaRepository
) : SeatStatusTypePojoRepository {

    override fun findByCode(code: String): SeatStatusType? {
        return jpaRepository.findByCode(code)
    }

    override fun findByIsActiveTrue(): List<SeatStatusType> {
        return jpaRepository.findByIsActiveTrue()
    }

    override fun findByCodeAndIsActiveTrue(code: String): SeatStatusType? {
        return jpaRepository.findByCodeAndIsActiveTrue(code)
    }

    @Cacheable(value = ["status:types"], key = "'all'")
    override fun findAll(): List<SeatStatusType> {
        return jpaRepository.findAll()
    }

    @CacheEvict(value = ["status:types"], allEntries = true)
    override fun save(statusType: SeatStatusType): SeatStatusType {
        return jpaRepository.save(statusType)
    }

    @CacheEvict(value = ["status:types"], allEntries = true)
    override fun deleteByCode(code: String) {
        val statusType = findByCode(code)
        if (statusType != null) {
            jpaRepository.delete(statusType)
        }
    }
    
    // ========== 상태 존재 여부 체크 ==========
    @Cacheable(value = ["status:types"], key = "'exists:' + #code")
    override fun existsByCodeAndIsActiveTrue(code: String): Boolean {
        return jpaRepository.existsByCodeAndIsActiveTrue(code)
    }
    
    // ========== 상태 조회 with 예외 처리 ==========
    @Cacheable(value = ["status:types"], key = "'AVAILABLE'")
    override fun getAvailableStatus(): SeatStatusType {
        return findByCodeAndIsActiveTrue("AVAILABLE")
            ?: throw IllegalStateException("AVAILABLE 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = ["status:types"], key = "'RESERVED'")
    override fun getReservedStatus(): SeatStatusType {
        return findByCodeAndIsActiveTrue("RESERVED")
            ?: throw IllegalStateException("RESERVED 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = ["status:types"], key = "'OCCUPIED'")
    override fun getOccupiedStatus(): SeatStatusType {
        return findByCodeAndIsActiveTrue("OCCUPIED")
            ?: throw IllegalStateException("OCCUPIED 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = ["status:types"], key = "'MAINTENANCE'")
    override fun getMaintenanceStatus(): SeatStatusType {
        return findByCodeAndIsActiveTrue("MAINTENANCE")
            ?: throw IllegalStateException("MAINTENANCE 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = ["status:types"], key = "'code:' + #code")
    override fun getStatusByCodeOrThrow(code: String): SeatStatusType {
        return findByCodeAndIsActiveTrue(code)
            ?: throw IllegalStateException("$code 상태를 찾을 수 없습니다")
    }
    
    // ========== 비즈니스 로직 메서드들 ==========
    override fun getAllActiveStatuses(): List<SeatStatusType> {
        return findByIsActiveTrue()
    }
    
    override fun isValidStatus(code: String): Boolean {
        return existsByCodeAndIsActiveTrue(code)
    }
}
