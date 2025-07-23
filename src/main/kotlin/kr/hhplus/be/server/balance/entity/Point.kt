package kr.hhplus.be.server.balance.entity

import jakarta.persistence.*
import kr.hhplus.be.server.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.balance.exception.InsufficientBalanceException
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "point")
class Point(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val pointId: Long = 0,
    
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,
    
    @Column(name = "last_updated", nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    
    // Point -> User 연관관계 (1:1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: kr.hhplus.be.server.user.entity.User? = null
    
    companion object {
        fun create(userId: Long, amount: BigDecimal): Point {
            if (amount < BigDecimal.ZERO) {
                throw InvalidPointAmountException("포인트 잔액은 음수일 수 없습니다")
            }
            
            return Point(
                userId = userId,
                amount = amount
            )
        }
    }
    
    fun charge(chargeAmount: BigDecimal): Point {
        if (chargeAmount <= BigDecimal.ZERO) {
            throw InvalidPointAmountException("충전 금액은 0보다 커야 합니다")
        }
        
        this.amount = this.amount.add(chargeAmount)
        this.lastUpdated = LocalDateTime.now()

        return this
    }
    
    fun deduct(deductAmount: BigDecimal): Point  {
        if (deductAmount <= BigDecimal.ZERO) {
            throw InvalidPointAmountException("차감 금액은 0보다 커야 합니다")
        }
        
        if (this.amount < deductAmount) {
            throw InsufficientBalanceException("잔액이 부족합니다. 현재 잔액: ${this.amount}, 차감 요청: ${deductAmount}")
        }
        
        this.amount = this.amount.subtract(deductAmount)
        this.lastUpdated = LocalDateTime.now()

        return this
    }
    
    fun hasEnoughBalance(amount: BigDecimal): Boolean {
        return this.amount >= amount
    }
}
