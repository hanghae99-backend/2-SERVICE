package kr.hhplus.be.server.global.client

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class ConcertDataPlatformClient(
    private val restTemplate: RestTemplate
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Value("\${mock-api.base-url:http://localhost:8080}")
    private lateinit var baseUrl: String
    
    @Async
    fun sendReservationData(
        reservationId: Long,
        userId: Long,
        concertId: Long,
        seatId: Long,
        operationType: String = "RESERVATION"
    ) {
        try {
            val reservationData = ConcertReservationData(
                reservationId = reservationId,
                userId = userId,
                concertId = concertId,
                seatId = seatId,
                operationType = operationType
            )
            
            val apiUrl = "$baseUrl/internal/test/mock/data-platform/reservations"
            
            logger.info { "🚀 데이터 플랫폼 전송 시작 - $operationType: reservationId=$reservationId" }
            
            val response = restTemplate.postForEntity(
                apiUrl,
                reservationData,
                String::class.java
            )
            
            if (response.statusCode.is2xxSuccessful) {
                logger.info { "✅ 데이터 플랫폼 전송 성공 - $operationType: reservationId=$reservationId" }
            } else {
                logger.warn { "⚠️ 데이터 플랫폼 응답 오류 - status: ${response.statusCode}" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ 데이터 플랫폼 전송 실패 - $operationType: reservationId=$reservationId" }
        }
    }
    
    @Async
    fun sendPaymentData(
        paymentId: Long,
        userId: Long,
        reservationId: Long?,
        amount: java.math.BigDecimal,
        operationType: String = "PAYMENT"
    ) {
        try {
            val paymentData = ConcertPaymentData(
                paymentId = paymentId,
                userId = userId,
                reservationId = reservationId,
                amount = amount,
                operationType = operationType
            )
            
            val apiUrl = "$baseUrl/internal/test/mock/data-platform/payments"
            
            logger.info { "🚀 데이터 플랫폼 결제 정보 전송 시작 - $operationType: paymentId=$paymentId" }
            
            val response = restTemplate.postForEntity(
                apiUrl,
                paymentData,
                String::class.java
            )
            
            if (response.statusCode.is2xxSuccessful) {
                logger.info { "✅ 데이터 플랫폼 결제 정보 전송 성공 - $operationType: paymentId=$paymentId" }
            } else {
                logger.warn { "⚠️ 데이터 플랫폼 결제 응답 오류 - status: ${response.statusCode}" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ 데이터 플랫폼 결제 정보 전송 실패 - $operationType: paymentId=$paymentId" }
        }
    }
}

data class ConcertReservationData(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val operationType: String
)

data class ConcertPaymentData(
    val paymentId: Long,
    val userId: Long,
    val reservationId: Long?,
    val amount: java.math.BigDecimal,
    val operationType: String
)