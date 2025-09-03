package kr.hhplus.be.server.config.mock

import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.global.client.SeatApiClient
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트용 SeatApiClient 모킹 구현체
 */
class MockSeatApiClient : SeatApiClient(
    RestTemplate(), // 실제로는 사용하지 않음
    "http://mock"   // 실제로는 사용하지 않음
) {
    
    private val seatData: ConcurrentHashMap<Long, SeatDto> = ConcurrentHashMap()
    private val reservedSeats: ConcurrentHashMap<Long, Boolean> = ConcurrentHashMap()
    
    init {
        // 기본 좌석 데이터 설정 - 더 넓은 범위로 확장
        (1L..3000L).forEach { seatId ->
            seatData[seatId] = SeatDto(
                seatId = seatId,
                scheduleId = 1L,
                seatNumber = seatId.toString(),
                price = BigDecimal("50000"),
                statusCode = "AVAILABLE"
            )
        }
    }
    

    
    override fun reserveSeat(seatId: Long): CommonApiResponse<*> {
        ensureSeatExists(seatId)
        val seat = seatData[seatId]!!
        
        if (reservedSeats[seatId] == true) {
            return CommonApiResponse.error<Map<String, Any>>(
                message = "이미 예약된 좌석입니다",
                errorCode = "SEAT.ALREADY_RESERVED"
            )
        }
        
        reservedSeats[seatId] = true
        val responseData = mapOf("seatId" to seatId, "status" to "RESERVED")
        return CommonApiResponse.success(
            data = responseData,
            message = "좌석 예약 성공"
        )
    }
    
    override fun validateSeatAvailability(seatId: Long): CommonApiResponse<*> {
        ensureSeatExists(seatId)
        val seat = seatData[seatId]!!
        val isAvailable = reservedSeats[seatId] != true
        
        val responseData = mapOf(
            "seatId" to seatId,
            "available" to isAvailable,
            "status" to if (isAvailable) "AVAILABLE" else "RESERVED"
        )
        return CommonApiResponse.success(
            data = responseData,
            message = "좌석 가용성 확인 완료"
        )
    }
    
    fun setSeatData(seatId: Long, seatDto: SeatDto) {
        seatData[seatId] = seatDto
    }
    
    fun reserveSeatForTest(seatId: Long) {
        reservedSeats[seatId] = true
    }
    
    fun releaseSeatForTest(seatId: Long) {
        reservedSeats.remove(seatId)
    }
    
    fun clear() {
        reservedSeats.clear()
    }

    /**
     * 동적으로 좌석 데이터를 생성하는 메서드
     */
    fun ensureSeatExists(seatId: Long) {
        if (!seatData.containsKey(seatId)) {
            seatData[seatId] = SeatDto(
                seatId = seatId,
                scheduleId = 1L,
                seatNumber = seatId.toString(),
                price = BigDecimal("50000"),
                statusCode = "AVAILABLE"
            )
        }
    }

    /**
     * 좌석 정보 조회 - 좌석이 없으면 자동 생성
     */
    override fun getSeatInfo(seatId: Long): SeatDto {
        ensureSeatExists(seatId)
        return seatData[seatId]!!
    }
}