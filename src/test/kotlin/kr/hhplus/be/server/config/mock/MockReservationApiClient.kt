package kr.hhplus.be.server.config.mock

import kr.hhplus.be.server.global.client.ReservationApiClient
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.web.client.RestTemplate

class MockReservationApiClient : ReservationApiClient(
    RestTemplate(),
    "http://mock:8080"
) {
    
    override fun confirmReservation(reservationId: Long, paymentId: Long): CommonApiResponse<*> {
        return CommonApiResponse.success<Any>("예약 확정 성공")
    }
}
