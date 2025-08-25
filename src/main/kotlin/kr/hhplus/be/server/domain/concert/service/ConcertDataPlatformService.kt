package kr.hhplus.be.server.domain.concert.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class ConcertDataPlatformService {
    
    private val logger = KotlinLogging.logger {}
    
    fun sendReservationData(reservationData: ConcertReservationData) {
        try {
            // Mock API 호출 - 실제 환경에서는 실제 데이터 플랫폼 엔드포인트로 변경
            val mockApiUrl = "https://mock-data-platform.example.com/api/reservations"
            
            // 실제로는 HTTP 호출, 여기서는 시뮬레이션
            logger.info { "데이터 플랫폼 전송 시작 - reservationId: ${reservationData.reservationId}" }
            
            // Mock API 응답 시뮬레이션
            Thread.sleep(100) // 네트워크 지연 시뮬레이션
            
            logger.info { "데이터 플랫폼 전송 완료 - reservationId: ${reservationData.reservationId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "데이터 플랫폼 전송 실패 - reservationId: ${reservationData.reservationId}" }
            // 실패해도 핵심 비즈니스에는 영향 없음
        }
    }
}

data class ConcertReservationData(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val concertTitle: String,
    val scheduleId: Long,
    val concertDate: LocalDateTime,
    val venue: String,
    val seatId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val paymentId: Long,
    val reservedAt: LocalDateTime
)