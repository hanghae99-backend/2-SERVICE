package kr.hhplus.be.server.domain.reservation.infrastructure

import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repository.ReservationStatusTypePojoRepository
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

    override fun findAll(): List<ReservationStatusType> {
        return jpaRepository.findAll()
    }

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
    override fun existsByCodeAndIsActiveTrue(code: String): Boolean {
        return jpaRepository.existsByCodeAndIsActiveTrue(code)
    }
    
    // ========== 상태 조회 with 예외 처리 ==========
    override fun getTemporaryStatus(): ReservationStatusType {
        return findByCodeAndIsActiveTrue("TEMPORARY")
            ?: throw IllegalStateException("TEMPORARY 상태를 찾을 수 없습니다")
    }
    
    override fun getConfirmedStatus(): ReservationStatusType {
        return findByCodeAndIsActiveTrue("CONFIRMED")
            ?: throw IllegalStateException("CONFIRMED 상태를 찾을 수 없습니다")
    }
    
    override fun getCancelledStatus(): ReservationStatusType {
        return findByCodeAndIsActiveTrue("CANCELLED")
            ?: throw IllegalStateException("CANCELLED 상태를 찾을 수 없습니다")
    }
    
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
}