package kr.hhplus.be.server.payment.repository

import kr.hhplus.be.server.payment.entity.Payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun existsByUserId(userId: Long): Boolean
    fun findAll(): List<Payment>
    fun delete(payment: Payment)
}
