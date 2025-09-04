package kr.hhplus.be.server.global.client

import kr.hhplus.be.server.internal.balance.dto.request.DeductBalanceRequest
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

@Component
open class BalanceApiClient(
    private val restTemplate: RestTemplate,
    @Value("\${app.api.base-url:http://localhost:8080}")
    private val baseUrl: String
) {

    fun deductBalance(userId: Long, amount: BigDecimal, description: String): CommonApiResponse<*> {
        val request = DeductBalanceRequest(userId, amount, description)
        return HttpClientUtil.postWithRetry(
            restTemplate,
            "$baseUrl/internal/balance/deduct",
            request,
            CommonApiResponse::class.java,
            "잔고 차감 - userId: $userId, amount: $amount"
        )
    }
}