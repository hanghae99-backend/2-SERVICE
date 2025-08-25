package kr.hhplus.be.server.domain.concert.service

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class ConcertDataPlatformService(
    private val restTemplate: RestTemplate
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Value("\${mock-api.base-url:http://localhost:8080}")
    private lateinit var baseUrl: String
    
    fun sendReservationData(reservationData: ConcertReservationData) {
        try {
            val apiUrl = "$baseUrl/api/test/mock/data-platform/reservations"
            
            logger.info { "🚀 데이터 플랫폼 전송 시작 - reservationId: ${reservationData.reservationId}" }
            
            val response = restTemplate.postForEntity(
                apiUrl,
                reservationData,
                String::class.java
            )
            
            if (response.statusCode.is2xxSuccessful) {
                logger.info { "✅ 데이터 플랫폼 전송 성공 - reservationId: ${reservationData.reservationId}" }
            } else {
                logger.warn { "⚠️ 데이터 플랫폼 응답 오류 - status: ${response.statusCode}" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ 데이터 플랫폼 전송 실패 - reservationId: ${reservationData.reservationId}" }
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