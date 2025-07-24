package kr.hhplus.be.server.payment.repository

import kr.hhplus.be.server.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {
    
    // 사용자 ID로 존재 여부 확인
    fun existsByUserId(userId: Long): Boolean
}
