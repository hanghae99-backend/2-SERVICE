package kr.hhplus.be.server.config.mock

import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

class MockConcertDataPlatformClient : ConcertDataPlatformClient(
    RestTemplate()
) {
    
    override fun sendReservationData(
        reservationId: Long,
        userId: Long,
        concertId: Long,
        seatId: Long,
        operationType: String
    ) {
        // Mock implementation - do nothing
    }
    
    override fun sendPaymentData(
        paymentId: Long,
        userId: Long,
        reservationId: Long?,
        amount: BigDecimal,
        operationType: String
    ) {
        // Mock implementation - do nothing
    }
}
