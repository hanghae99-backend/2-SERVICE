package kr.hhplus.be.server.global.client

import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.global.response.CommonApiResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class SeatApiClient(
    private val restTemplate: RestTemplate,
    @Value("\${app.api.base-url:http://localhost:8080}")
    private val baseUrl: String
) {
    private val logger = KotlinLogging.logger {}

    fun getSeatInfo(seatId: Long): SeatDto {
        try {
            val response = restTemplate.exchange(
                "$baseUrl/internal/seats/$seatId",
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<CommonApiResponse<SeatDto>>() {}
            )
            
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                return response.body!!.data!!
            } else {
                throw RuntimeException("좌석 정보 조회 실패 - HTTP ${response.statusCode}")
            }
        } catch (e: Exception) {
            logger.error(e) { "좌석 정보 조회 실패 - seatId: $seatId" }
            throw e
        }
    }

    fun reserveSeat(seatId: Long): CommonApiResponse<*> {
        try {
            val response = restTemplate.exchange(
                "$baseUrl/internal/seats/$seatId/reserve",
                HttpMethod.POST,
                null,
                CommonApiResponse::class.java
            )
            
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                return response.body!!
            } else {
                throw RuntimeException("좌석 예약 실패 - HTTP ${response.statusCode}")
            }
        } catch (e: Exception) {
            logger.error(e) { "좌석 예약 실패 - seatId: $seatId" }
            throw e
        }
    }

    fun validateSeatAvailability(seatId: Long): CommonApiResponse<*> {
        try {
            val response = restTemplate.exchange(
                "$baseUrl/internal/seats/$seatId/validate",
                HttpMethod.GET,
                null,
                CommonApiResponse::class.java
            )
            
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                return response.body!!
            } else {
                throw RuntimeException("좌석 가용성 검증 실패 - HTTP ${response.statusCode}")
            }
        } catch (e: Exception) {
            logger.error(e) { "좌석 가용성 검증 실패 - seatId: $seatId" }
            throw e
        }
    }
}