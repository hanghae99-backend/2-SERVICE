package kr.hhplus.be.server.domain.balance.infrastructure

import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import org.springframework.stereotype.Repository

@Repository
class PointHistoryTypeRepositoryImpl(
    private val jpaRepository: PointHistoryTypeJpaRepository
) : PointHistoryTypePojoRepository {

    override fun findByCode(code: String): PointHistoryType? {
        return jpaRepository.findByCode(code)
    }

    override fun findByIsActiveTrue(): List<PointHistoryType> {
        return jpaRepository.findByIsActiveTrue()
    }

    override fun findByCodeAndIsActiveTrue(code: String): PointHistoryType? {
        return jpaRepository.findByCodeAndIsActiveTrue(code)
    }

    override fun findAll(): List<PointHistoryType> {
        return jpaRepository.findAll()
    }

    override fun save(historyType: PointHistoryType): PointHistoryType {
        return jpaRepository.save(historyType)
    }

    override fun deleteByCode(code: String) {
        val historyType = findByCode(code)
        if (historyType != null) {
            jpaRepository.delete(historyType)
        }
    }
    
    // ========== 상태 존재 여부 체크 ==========
    override fun existsByCodeAndIsActiveTrue(code: String): Boolean {
        return jpaRepository.existsByCodeAndIsActiveTrue(code)
    }
    
    // ========== 상태 조회 with 예외 처리 ==========
    override fun getChargeType(): PointHistoryType {
        return findByCodeAndIsActiveTrue("CHARGE")
            ?: throw IllegalStateException("CHARGE 타입을 찾을 수 없습니다")
    }
    
    override fun getUseType(): PointHistoryType {
        return findByCodeAndIsActiveTrue("USE")
            ?: throw IllegalStateException("USE 타입을 찾을 수 없습니다")
    }
    
    override fun getTypeByCodeOrThrow(code: String): PointHistoryType {
        return findByCodeAndIsActiveTrue(code)
            ?: throw IllegalStateException("$code 타입을 찾을 수 없습니다")
    }
    
    // ========== 비즈니스 로직 메서드들 ==========
    override fun getAllActiveTypes(): List<PointHistoryType> {
        return findByIsActiveTrue()
    }
    
    override fun isValidType(code: String): Boolean {
        return existsByCodeAndIsActiveTrue(code)
    }

    override fun deleteAll() {
        jpaRepository.deleteAll()
    }
    
    override fun flush() {
        jpaRepository.flush()
    }
}
