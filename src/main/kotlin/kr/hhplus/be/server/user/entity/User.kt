package kr.hhplus.be.server.user.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.entity.PointHistory
import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.reservation.entity.Reservation
import java.time.LocalDateTime

@Entity
@Table(name = "user")
class User(
    @Id
    @Column(name = "user_id")
    val userId: Long,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL])
    val point: Point? = null

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    private var _pointHistoryList: MutableList<PointHistory> = mutableListOf()

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private var _reservationList: MutableList<Reservation> = mutableListOf()
    
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private var _paymentList: MutableList<Payment> = mutableListOf()
    
    val pointHistoryList: List<PointHistory>
        get() = _pointHistoryList.toList()
    
    val reservationList: List<Reservation>
        get() = _reservationList.toList()
    
    val paymentList: List<Payment>
        get() = _paymentList.toList()

    fun addPointHistory(pointHistory: PointHistory) {
        if (!_pointHistoryList.contains(pointHistory)) {
            _pointHistoryList.add(pointHistory)
        }
    }
    
    fun addReservation(reservation: Reservation) {
        if (!_reservationList.contains(reservation)) {
            _reservationList.add(reservation)
        }
    }
    
    fun addPayment(payment: Payment) {
        if (!_paymentList.contains(payment)) {
            _paymentList.add(payment)
        }
    }
    
    internal fun getInternalPointHistoryList() = _pointHistoryList
    internal fun getInternalReservationList() = _reservationList
    internal fun getInternalPaymentList() = _paymentList
    
    companion object {
        fun create(userId: Long): User {
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            
            return User(
                userId = userId
            )
        }
    }
}
