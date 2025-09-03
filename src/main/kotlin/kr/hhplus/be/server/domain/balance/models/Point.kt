package kr.hhplus.be.server.domain.balance.models

import kr.hhplus.be.server.global.common.BaseEntity
import jakarta.persistence.*
import kr.hhplus.be.server.domain.balance.exception.InvalidAmountException
import kr.hhplus.be.server.domain.balance.exception.InsufficientBalanceException
import kr.hhplus.be.server.domain.balance.rules.BalanceBusinessRules
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "point",
    indexes = [
        Index(name = "idx_point_user_id", columnList = "user_id"),
        Index(name = "idx_point_last_updated", columnList = "last_updated")
    ]
)
class Point(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var pointId: Long = 0,

    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long,

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now(),

) : BaseEntity() {

    companion object {
        fun create(userId: Long, amount: BigDecimal): Point {
            if (amount < BigDecimal.ZERO) {
                throw InvalidAmountException(amount)
            }

            return Point(
                userId = userId,
                amount = amount
            )
        }
    }

    fun charge(chargeAmount: BigDecimal) {
        BalanceBusinessRules.validateChargeAmount(chargeAmount)
        BalanceBusinessRules.validateBalanceLimit(this.amount, chargeAmount)

        this.amount = this.amount.add(chargeAmount)
        this.lastUpdated = LocalDateTime.now()
    }

    fun deduct(deductAmount: BigDecimal) {
        validateDeductAmount(deductAmount)
        validateSufficientBalance(deductAmount)

        this.amount = this.amount.subtract(deductAmount)
        this.lastUpdated = LocalDateTime.now()
    }

    private fun validateDeductAmount(deductAmount: BigDecimal) {
        if (deductAmount <= BigDecimal.ZERO) {
            throw InvalidAmountException(deductAmount)
        }
    }

    private fun validateSufficientBalance(deductAmount: BigDecimal) {
        if (this.amount < deductAmount) {
            throw InsufficientBalanceException(this.userId, this.amount, deductAmount)
        }
    }

    fun hasEnoughBalance(amount: BigDecimal): Boolean {
        return this.amount >= amount
    }
}