package kr.hhplus.be.server.global.client

import kr.hhplus.be.server.api.reservation.dto.request.ReservationConfirmRequest
import kr.hhplus.be.server.global.response.CommonApiResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
open class ReservationApiClient(
    private val restTemplate: RestTemplate,
    @Value("\${app.api.base-url:http://localhost:8080}")
    private val baseUrl: String
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 예약 확정 API 호출
     */
    open fun confirmReservation(reservationId: Long, paymentId: Long): CommonApiResponse<*> {
        val request = ReservationConfirmRequest(paymentId = paymentId)
        
        try {
            val response = restTemplate.exchange(
                "$baseUrl/internal/reservations/$reservationId/confirm",
                org.springframework.http.HttpMethod.PUT,
                org.springframework.http.HttpEntity(request),
                CommonApiResponse::class.java
            )
            
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                return response.body!!
            } else {
                throw RuntimeException("예약 확정 실패 - HTTP ${response.statusCode}")
            }
        } catch (e: Exception) {
            logger.error(e) { "예약 확정 실패 - reservationId: $reservationId" }
            throw e
        }
    }
}