package kr.hhplus.be.server.domain.balance.event

import kr.hhplus.be.server.global.event.AbstractDomainEvent
import java.math.BigDecimal

data class BalanceChargedEvent(
    val userId: Long,
    val amount: BigDecimal,
    val newBalance: BigDecimal
) : AbstractDomainEvent() {
    override val eventType: String = "BalanceCharged"
}

data class BalanceDeductedEvent(
    val userId: Long,
    val amount: BigDecimal,
    val remainingBalance: BigDecimal,
    val relatedId: Long? = null
) : AbstractDomainEvent() {
    override val eventType: String = "BalanceDeducted"
}
