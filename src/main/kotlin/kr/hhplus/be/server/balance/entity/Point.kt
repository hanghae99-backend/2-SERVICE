package kr.hhplus.be.server.balance.entity

import jakarta.persistence.*
import kr.hhplus.be.server.balance.entity.InvalidPointAmountException
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "point")
data class Point(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_id")
    val pointId: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    
    @Column(name = "last_updated", nullable = false)
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    
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
        
        return this.copy(
            amount = this.amount.add(chargeAmount),
            lastUpdated = LocalDateTime.now()
        )
    }
    
    fun deduct(deductAmount: BigDecimal): Point {
        if (deductAmount <= BigDecimal.ZERO) {
            throw InvalidPointAmountException("차감 금액은 0보다 커야 합니다")
        }
        
        if (this.amount < deductAmount) {
            throw InsufficientBalanceException("잔액이 부족합니다. 현재 잔액: ${this.amount}, 차감 요청: ${deductAmount}")
        }
        
        return this.copy(
            amount = this.amount.subtract(deductAmount),
            lastUpdated = LocalDateTime.now()
        )
    }
    
    fun hasEnoughBalance(amount: BigDecimal): Boolean {
        return this.amount >= amount
    }
}
