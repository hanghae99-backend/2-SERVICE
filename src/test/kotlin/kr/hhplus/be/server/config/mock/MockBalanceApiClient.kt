package kr.hhplus.be.server.config.mock

import kr.hhplus.be.server.global.client.BalanceApiClient
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

class MockBalanceApiClient : BalanceApiClient(
    RestTemplate(),
    "http://mock:8080"
) {
    
    override fun deductBalance(userId: Long, amount: BigDecimal, description: String): CommonApiResponse<*> {
        return CommonApiResponse.success<Any>("잔고 차감 성공")
    }
}
