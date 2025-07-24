package kr.hhplus.be.server.payment.repository

import kr.hhplus.be.server.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {
    
    // 사용자별 결제 내역 조회 (paidAt 기준 정렬)
    fun findByUserIdOrderByPaidAtDesc(userId: Long): List<Payment>
    
    // 사용자별 결제 내역 조회 (ID 기준 정렬 - createdAt 대신)
    fun findByUserIdOrderByPaymentIdDesc(userId: Long): List<Payment>
    
    // 특정 상태의 결제 조회
    fun findByStatusCode(statusCode: String): List<Payment>
    
    // 사용자별 특정 상태 결제 조회
    fun findByUserIdAndStatusCode(userId: Long, statusCode: String): List<Payment>
    
    // 사용자 ID로 존재 여부 확인
    fun existsByUserId(userId: Long): Boolean
}
