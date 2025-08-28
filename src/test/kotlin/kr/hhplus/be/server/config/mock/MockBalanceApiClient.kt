package kr.hhplus.be.server.config.mock

import kr.hhplus.be.server.global.client.BalanceApiClient
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트용 BalanceApiClient 모킹 구현체
 */
class MockBalanceApiClient : BalanceApiClient(
    RestTemplate(), // 실제로는 사용하지 않음
    "http://mock"   // 실제로는 사용하지 않음
) {
    
    private val balances: ConcurrentHashMap<Long, BigDecimal> = ConcurrentHashMap()
    
    init {
        // 기본 잔액 설정
        (1L..1000000L).forEach { userId ->
            balances[userId] = BigDecimal("200000") // 20만원
        }
    }
    
    override fun deductBalance(userId: Long, amount: BigDecimal, description: String): CommonApiResponse<*> {
        val currentBalance = balances[userId] ?: BigDecimal.ZERO
        
        if (currentBalance >= amount) {
            balances[userId] = currentBalance - amount
            val responseData = mapOf(
                "userId" to userId,
                "deductedAmount" to amount,
                "remainingBalance" to (balances[userId] ?: BigDecimal.ZERO)
            )
            return CommonApiResponse.success(
                data = responseData,
                message = "잔액 차감 성공"
            )
        } else {
            return CommonApiResponse.error<Map<String, Any>>(
                message = "잔액이 부족합니다",
                errorCode = "BALANCE.INSUFFICIENT"
            )
        }
    }
    
    fun setBalance(userId: Long, balance: BigDecimal) {
        balances[userId] = balance
    }
    
    fun getBalance(userId: Long): BigDecimal {
        return balances[userId] ?: BigDecimal.ZERO
    }
}