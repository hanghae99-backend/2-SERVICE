package kr.hhplus.be.server.domain.payment.infrastructure

import kr.hhplus.be.server.domain.payment.models.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    
    // 사용자 ID로 존재 여부 확인
    fun existsByUserId(userId: Long): Boolean
    
    // 예약 ID로 결제 내역 조회
    fun findByReservationId(reservationId: Long): List<Payment>
    
    // 사용자 ID로 결제 내역 조회
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Payment>
    
    // 예약 ID와 상태로 결제 조회
    fun findByReservationIdAndStatusCode(reservationId: Long, statusCode: String): Payment?
    
    // 사용자 ID와 상태로 결제 내역 조회
    fun findByUserIdAndStatusCodeOrderByCreatedAtDesc(userId: Long, statusCode: String): List<Payment>
}
