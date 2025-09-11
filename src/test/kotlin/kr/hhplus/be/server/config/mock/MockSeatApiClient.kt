package kr.hhplus.be.server.config.mock

import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.global.client.SeatApiClient
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

class MockSeatApiClient : SeatApiClient(
    RestTemplate(),
    "http://mock:8080"
) {
    
    override fun getSeatInfo(seatId: Long): SeatDto {
        return SeatDto(
            seatId = seatId,
            scheduleId = 1L,
            seatNumber = "A1",
            price = BigDecimal("50000"),
            statusCode = "AVAILABLE"
        )
    }
    
    override fun reserveSeat(seatId: Long): CommonApiResponse<*> {
        return CommonApiResponse.success<Any>("좌석 예약 성공")
    }
    
    override fun validateSeatAvailability(seatId: Long): CommonApiResponse<*> {
        return CommonApiResponse.success<Any>("좌석 가용성 확인 성공")
    }
}
