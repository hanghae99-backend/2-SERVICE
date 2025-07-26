package kr.hhplus.be.server.payment.repository.impl

import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.payment.repository.PaymentRepository
import kr.hhplus.be.server.payment.repository.PaymentJpaRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository
) : PaymentRepository {
    
    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }
    
    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findById(id).orElse(null)
    }
    
    override fun existsByUserId(userId: Long): Boolean {
        return paymentJpaRepository.existsByUserId(userId)
    }
    
    override fun findAll(): List<Payment> {
        return paymentJpaRepository.findAll()
    }
    
    override fun delete(payment: Payment) {
        paymentJpaRepository.delete(payment)
    }
}
