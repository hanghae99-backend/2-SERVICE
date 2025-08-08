package kr.hhplus.be.server.config

import kr.hhplus.be.server.domain.balance.infrastructure.PointJpaRepository
import kr.hhplus.be.server.domain.balance.infrastructure.PointHistoryJpaRepository
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.payment.infrastructure.PaymentJpaRepository
import kr.hhplus.be.server.domain.reservation.infrastructure.ReservationJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertScheduleJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertJpaRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import jakarta.persistence.EntityManager
import org.springframework.test.annotation.DirtiesContext

@Service
@Profile("test")
class TestDataCleanupService(
    private val entityManager: EntityManager,
    private val paymentJpaRepository: PaymentJpaRepository? = null,
    private val reservationJpaRepository: ReservationJpaRepository? = null,
    private val seatJpaRepository: SeatJpaRepository? = null,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository? = null,
    private val concertJpaRepository: ConcertJpaRepository? = null,
    private val pointHistoryJpaRepository: PointHistoryJpaRepository? = null,
    private val pointJpaRepository: PointJpaRepository,
    private val userJpaRepository: UserJpaRepository
) {

    @Transactional
    fun cleanupAllTestData() {
        try {
            // 외래 키 제약 조건 비활성화
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate()
            entityManager.flush()
            
            // 의존성 순서에 따라 삭제 (자식 테이블부터)
            try { 
                paymentJpaRepository?.deleteAll()
                entityManager.flush()
            } catch (e: Exception) { /* 무시 */ }
            
            try { 
                reservationJpaRepository?.deleteAll()
                entityManager.flush()
            } catch (e: Exception) { /* 무시 */ }
            
            try { 
                seatJpaRepository?.deleteAll()
                entityManager.flush()
            } catch (e: Exception) { /* 무시 */ }
            
            try { 
                concertScheduleJpaRepository?.deleteAll()
                entityManager.flush()
            } catch (e: Exception) { /* 무시 */ }
            
            try { 
                concertJpaRepository?.deleteAll()
                entityManager.flush()
            } catch (e: Exception) { /* 무시 */ }
            
            try { 
                pointHistoryJpaRepository?.deleteAll()
                entityManager.flush()
            } catch (e: Exception) { /* 무시 */ }
            
            try { 
                pointJpaRepository.deleteAll()
                entityManager.flush()
            } catch (e: Exception) { /* 무시 */ }
            
            try { 
                userJpaRepository.deleteAll()
                entityManager.flush()
            } catch (e: Exception) { /* 무시 */ }
            
            // 외래 키 제약 조건 재활성화
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate()
            entityManager.flush()
            
        } catch (e: Exception) {
            // 실패 시에도 제약 조건 복원 시도
            try {
                entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate()
                entityManager.flush()
            } catch (ex: Exception) {
                // 무시
            }
        }
    }
}
