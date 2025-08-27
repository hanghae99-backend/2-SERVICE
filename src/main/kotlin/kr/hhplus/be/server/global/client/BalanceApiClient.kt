package kr.hhplus.be.server.global.client

import kr.hhplus.be.server.internal.balance.dto.request.DeductBalanceRequest
import kr.hhplus.be.server.global.response.CommonApiResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

@Component
class BalanceApiClient(
    private val restTemplate: RestTemplate,
    @Value("\${app.api.base-url:http://localhost:8080}")
    private val baseUrl: String
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 잔고 차감 API 호출
     */
    fun deductBalance(userId: Long, amount: BigDecimal, description: String): CommonApiResponse<*> {
        val request = DeductBalanceRequest(userId, amount, description)
        
        try {
            val response = restTemplate.postForEntity(
                "$baseUrl/internal/balance/deduct",
                request,
                CommonApiResponse::class.java
            )
            
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                return response.body!!
            } else {
                throw RuntimeException("잔고 차감 실패 - HTTP ${response.statusCode}")
            }
        } catch (e: Exception) {
            logger.error(e) { "잔고 차감 실패 - userId: $userId, amount: $amount" }
            throw e
        }
    }
}