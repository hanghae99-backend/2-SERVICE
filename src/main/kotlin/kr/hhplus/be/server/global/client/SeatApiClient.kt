package kr.hhplus.be.server.global.client

import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.global.response.CommonApiResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException

@Component
open class SeatApiClient(
    private val restTemplate: RestTemplate,
    @Value("\${app.api.base-url:http://localhost:8080}")
    private val baseUrl: String
) {
    private val logger = KotlinLogging.logger {}
    

    open fun getSeatInfo(seatId: Long): SeatDto {
        val response = HttpClientUtil.get(
            restTemplate,
            "$baseUrl/internal/seats/$seatId",
            object : ParameterizedTypeReference<CommonApiResponse<SeatDto>>() {},
            "좌석 정보 조회 - seatId: $seatId"
        )
        return response.data ?: throw RuntimeException("좌석 정보가 없습니다")
    }

    open fun reserveSeat(seatId: Long): CommonApiResponse<*> {
        return HttpClientUtil.postWithRetry(
            restTemplate,
            "$baseUrl/internal/seats/$seatId/reserve",
            null,
            CommonApiResponse::class.java,
            "좌석 예약 - seatId: $seatId"
        )
    }

    open fun validateSeatAvailability(seatId: Long): CommonApiResponse<*> {
        return HttpClientUtil.get(
            restTemplate,
            "$baseUrl/internal/seats/$seatId/validate",
            CommonApiResponse::class.java,
            "좌석 가용성 검증 - seatId: $seatId"
        )
    }
}