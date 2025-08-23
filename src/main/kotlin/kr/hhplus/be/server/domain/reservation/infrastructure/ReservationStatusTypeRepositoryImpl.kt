package kr.hhplus.be.server.domain.reservation.infrastructure

import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.global.constants.CacheConstants
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Repository

@Repository
class ReservationStatusTypeRepositoryImpl(
    private val jpaRepository: ReservationStatusTypeJpaRepository
) : ReservationStatusTypePojoRepository {

    override fun findByCode(code: String): ReservationStatusType? {
        return jpaRepository.findByCode(code)
    }

    override fun findByIsActiveTrue(): List<ReservationStatusType> {
        return jpaRepository.findByIsActiveTrue()
    }

    override fun findByCodeAndIsActiveTrue(code: String): ReservationStatusType? {
        return jpaRepository.findByCodeAndIsActiveTrue(code)
    }

    @Cacheable(value = [CacheConstants.RESERVATION_STATUS_TYPES], key = "'all'")
    override fun findAll(): List<ReservationStatusType> {
        return jpaRepository.findAll()
    }

    @CacheEvict(value = [CacheConstants.RESERVATION_STATUS_TYPES], allEntries = true)
    override fun save(statusType: ReservationStatusType): ReservationStatusType {
        return jpaRepository.save(statusType)
    }

    override fun deleteByCode(code: String) {
        val statusType = findByCode(code)
        if (statusType != null) {
            jpaRepository.delete(statusType)
        }
    }
    
    // ========== 상태 존재 여부 체크 ==========
    @Cacheable(value = [CacheConstants.RESERVATION_STATUS_TYPES], key = "'exists:' + #code")
    override fun existsByCodeAndIsActiveTrue(code: String): Boolean {
        return jpaRepository.existsByCodeAndIsActiveTrue(code)
    }
    
    // ========== 상태 조회 with 예외 처리 ==========
    @Cacheable(value = [CacheConstants.RESERVATION_STATUS_TYPES], key = "'status:TEMPORARY'")
    override fun getTemporaryStatus(): ReservationStatusType {
        return findByCodeAndIsActiveTrue(ReservationStatusType.TEMPORARY)
            ?: throw IllegalStateException("TEMPORARY 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = [CacheConstants.RESERVATION_STATUS_TYPES], key = "'status:CONFIRMED'")
    override fun getConfirmedStatus(): ReservationStatusType {
        return findByCodeAndIsActiveTrue(ReservationStatusType.CONFIRMED)
            ?: throw IllegalStateException("CONFIRMED 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = [CacheConstants.RESERVATION_STATUS_TYPES], key = "'status:CANCELLED'")
    override fun getCancelledStatus(): ReservationStatusType {
        return findByCodeAndIsActiveTrue(ReservationStatusType.CANCELLED)
            ?: throw IllegalStateException("CANCELLED 상태를 찾을 수 없습니다")
    }
    
    @Cacheable(value = [CacheConstants.RESERVATION_STATUS_TYPES], key = "'status:' + #code")
    override fun getStatusByCodeOrThrow(code: String): ReservationStatusType {
        return findByCodeAndIsActiveTrue(code)
            ?: throw IllegalStateException("$code 상태를 찾을 수 없습니다")
    }
    
    // ========== 비즈니스 로직 메서드들 ==========
    override fun getAllActiveStatuses(): List<ReservationStatusType> {
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