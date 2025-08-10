package kr.hhplus.be.server.domain.payment.repository

import kr.hhplus.be.server.domain.payment.models.Payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun findByReservationId(reservationId: Long): List<Payment>
    fun existsByUserId(userId: Long): Boolean
    fun findAll(): List<Payment>
    fun delete(payment: Payment)
    fun deleteAll() // 테스트용 - 모든 결제 데이터 삭제
}
