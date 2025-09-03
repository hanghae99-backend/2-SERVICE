package kr.hhplus.be.server.config.mock

import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트용 ConcertDataPlatformClient 모킹 구현체
 */
class MockConcertDataPlatformClient : ConcertDataPlatformClient(
    RestTemplate() // 실제로는 사용하지 않음
) {
    
    private val sentReservationData = ConcurrentHashMap<Long, Boolean>()
    private val sentPaymentData = ConcurrentHashMap<Long, Boolean>()
    
    override fun sendReservationData(
        reservationId: Long,
        userId: Long,
        concertId: Long,
        seatId: Long,
        operationType: String
    ) {
        // 비동기 작업이므로 성공으로 간주
        sentReservationData[reservationId] = true
    }
    
    override fun sendPaymentData(
        paymentId: Long,
        userId: Long,
        reservationId: Long?,
        amount: BigDecimal,
        operationType: String
    ) {
        // 비동기 작업이므로 성공으로 간주
        sentPaymentData[paymentId] = true
    }
    
    fun isReservationDataSent(reservationId: Long): Boolean {
        return sentReservationData[reservationId] ?: false
    }
    
    fun isPaymentDataSent(paymentId: Long): Boolean {
        return sentPaymentData[paymentId] ?: false
    }
    
    fun clear() {
        sentReservationData.clear()
        sentPaymentData.clear()
    }
}