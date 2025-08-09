package kr.hhplus.be.server.config

import kr.hhplus.be.server.domain.balance.infrastructure.PointJpaRepository
import kr.hhplus.be.server.domain.balance.infrastructure.PointHistoryJpaRepository
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.payment.infrastructure.PaymentJpaRepository
import kr.hhplus.be.server.domain.reservation.infrastructure.ReservationJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertScheduleJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertJpaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import jakarta.persistence.EntityManager

@Service
@Profile("test")
class TestDataCleanupService(
    private val entityManager: EntityManager
) {
    
    // 모든 Repository를 선택적으로 주입받도록 설정
    @Autowired(required = false)
    private var paymentJpaRepository: PaymentJpaRepository? = null
    
    @Autowired(required = false)
    private var reservationJpaRepository: ReservationJpaRepository? = null
    
    @Autowired(required = false)
    private var seatJpaRepository: SeatJpaRepository? = null
    
    @Autowired(required = false)
    private var concertScheduleJpaRepository: ConcertScheduleJpaRepository? = null
    
    @Autowired(required = false)
    private var concertJpaRepository: ConcertJpaRepository? = null
    
    @Autowired(required = false)
    private var pointHistoryJpaRepository: PointHistoryJpaRepository? = null
    
    @Autowired(required = false)
    private var pointJpaRepository: PointJpaRepository? = null
    
    @Autowired(required = false)
    private var userJpaRepository: UserJpaRepository? = null

    @Transactional
    fun cleanupAllTestData() {
        try {
            // 외래키 관계에 따른 올바른 삭제 순서
            
            // 1. Payment (reservation_id 참조) - 가장 먼저 삭제
            paymentJpaRepository?.let { 
                try { 
                    it.deleteAll()
                    entityManager.flush()
                } catch (e: Exception) { /* 무시 */ }
            }
            
            // 2. Reservation (user_id, seat_id 참조)
            reservationJpaRepository?.let { 
                try { 
                    it.deleteAll()
                    entityManager.flush()
                } catch (e: Exception) { /* 무시 */ }
            }
            
            // 3. Seat (schedule_id 참조)
            seatJpaRepository?.let { 
                try { 
                    it.deleteAll()
                    entityManager.flush()
                } catch (e: Exception) { /* 무시 */ }
            }
            
            // 4. ConcertSchedule (concert_id 참조)
            concertScheduleJpaRepository?.let { 
                try { 
                    it.deleteAll()
                    entityManager.flush()
                } catch (e: Exception) { /* 무시 */ }
            }
            
            // 5. Concert (독립적)
            concertJpaRepository?.let { 
                try { 
                    it.deleteAll()
                    entityManager.flush()
                } catch (e: Exception) { /* 무시 */ }
            }
            
            // 6. PointHistory (user_id 참조)
            pointHistoryJpaRepository?.let { 
                try { 
                    it.deleteAll()
                    entityManager.flush()
                } catch (e: Exception) { /* 무시 */ }
            }
            
            // 7. Point (user_id 참조)
            pointJpaRepository?.let { 
                try { 
                    it.deleteAll()
                    entityManager.flush()
                } catch (e: Exception) { /* 무시 */ }
            }
            
            // 8. User (마지막에 삭제)
            userJpaRepository?.let { 
                try { 
                    it.deleteAll()
                    entityManager.flush()
                } catch (e: Exception) { /* 무시 */ }
            }
            
        } catch (e: Exception) {
            // 실패해도 계속 진행
            println("TestDataCleanupService 실행 중 오류 발생: ${e.message}")
        }
    }
}
