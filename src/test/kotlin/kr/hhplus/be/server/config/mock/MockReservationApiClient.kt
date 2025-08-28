package kr.hhplus.be.server.config.mock

import kr.hhplus.be.server.global.client.ReservationApiClient
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.web.client.RestTemplate
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트용 ReservationApiClient 모킹 구현체  
 */
class MockReservationApiClient : ReservationApiClient(
    RestTemplate(), // 실제로는 사용하지 않음
    "http://mock"   // 실제로는 사용하지 않음
) {
    
    private val confirmedReservations: ConcurrentHashMap<Long, Long> = ConcurrentHashMap()
    // 예약 ID 검증을 위한 유효한 예약 ID 집합
    private val validReservationIds: ConcurrentHashMap<Long, Boolean> = ConcurrentHashMap()
    
    override fun confirmReservation(reservationId: Long, paymentId: Long): CommonApiResponse<*> {
        // 예약 ID 유효성 검증
        if (!validReservationIds.containsKey(reservationId)) {
            throw RuntimeException("예약을 찾을 수 없습니다: $reservationId")
        }
        
        confirmedReservations[reservationId] = paymentId
        
        val responseData = mapOf(
            "reservationId" to reservationId,
            "paymentId" to paymentId,
            "status" to "CONFIRMED"
        )
        return CommonApiResponse.success(
            data = responseData,
            message = "예약 확정 성공"
        )
    }
    
    fun addValidReservationId(reservationId: Long) {
        validReservationIds[reservationId] = true
    }
    
    fun isReservationConfirmed(reservationId: Long): Boolean {
        return confirmedReservations.containsKey(reservationId)
    }
    
    fun getPaymentIdForReservation(reservationId: Long): Long? {
        return confirmedReservations[reservationId]
    }
    
    fun clear() {
        confirmedReservations.clear()
        validReservationIds.clear()
    }
}