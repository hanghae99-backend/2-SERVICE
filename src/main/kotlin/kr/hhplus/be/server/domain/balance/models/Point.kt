package kr.hhplus.be.server.domain.balance.models

import kr.hhplus.be.server.global.common.BaseEntity

import jakarta.persistence.*
import kr.hhplus.be.server.domain.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.domain.balance.exception.InsufficientBalanceException
import kr.hhplus.be.server.domain.user.model.User
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
    var lastUpdated: LocalDateTime = LocalDateTime.now()
) : BaseEntity() {
    
    // Point -> User 연관관계 (1:1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null
    
    companion object {
        private val MAX_BALANCE = BigDecimal("50000000")
        private val MIN_CHARGE_AMOUNT = BigDecimal("1000")
        
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
        validateChargeAmount(chargeAmount)
        validateMaxBalance(chargeAmount)
        
        this.amount = this.amount.add(chargeAmount)
        this.lastUpdated = LocalDateTime.now()

        return this
    }
    
    private fun validateChargeAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw InvalidPointAmountException("충전 금액은 0보다 커야 합니다: $amount")
        }
        if (amount < MIN_CHARGE_AMOUNT) {
            throw InvalidPointAmountException("최소 충전 금액은 ${MIN_CHARGE_AMOUNT}원입니다: $amount")
        }
    }
    
    private fun validateMaxBalance(chargeAmount: BigDecimal) {
        val newAmount = this.amount.add(chargeAmount)
        if (newAmount > MAX_BALANCE) {
            throw InvalidPointAmountException(
                "최대 잔액 한도를 초과합니다. 현재: ${this.amount}, 충전 후: $newAmount, 한도: $MAX_BALANCE"
            )
        }
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
