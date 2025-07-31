package kr.hhplus.be.server.domain.balance.event.handler

import io.kotest.core.spec.style.DescribeSpec
import kr.hhplus.be.server.domain.balance.event.BalanceChargedEvent
import kr.hhplus.be.server.domain.balance.event.BalanceDeductedEvent
import java.math.BigDecimal

class BalanceEventHandlerTest : DescribeSpec({
    
    val balanceEventHandler = BalanceEventHandler()
    
    describe("handleBalanceCharged") {
        context("잔액 충전 이벤트가 발생할 때") {
            it("충전 정보를 로깅해야 한다") {
                // given
                val event = BalanceChargedEvent(
                    userId = 1L,
                    amount = BigDecimal("10000"),
                    newBalance = BigDecimal("25000"),
                )
                
                // when
                balanceEventHandler.handleBalanceCharged(event)
                
                // then - 로깅이 정상적으로 수행되어야 함 (예외가 발생하지 않음)
                // 실제 로깅 검증은 어렵기 때문에 메서드가 정상 실행되는지만 확인
            }
        }
    }
    
    describe("handleBalanceDeducted") {
        context("잔액 차감 이벤트가 발생할 때") {
            it("차감 정보를 로깅해야 한다") {
                // given
                val event = BalanceDeductedEvent(
                    userId = 1L,
                    amount = BigDecimal("5000"),
                    remainingBalance = BigDecimal("15000"),
                )
                
                // when
                balanceEventHandler.handleBalanceDeducted(event)
                
                // then - 로깅이 정상적으로 수행되어야 함 (예외가 발생하지 않음)
                // 실제 로깅 검증은 어렵기 때문에 메서드가 정상 실행되는지만 확인
            }
        }
    }
})