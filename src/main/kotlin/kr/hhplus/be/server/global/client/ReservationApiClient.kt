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

    open fun confirmReservation(reservationId: Long, paymentId: Long): CommonApiResponse<*> {
        val request = ReservationConfirmRequest(paymentId = paymentId)
        
        return HttpClientUtil.put(
            restTemplate,
            "$baseUrl/internal/reservations/$reservationId/confirm",
            request,
            CommonApiResponse::class.java,
            "예약 확정 - reservationId: $reservationId"
        )
    }
}