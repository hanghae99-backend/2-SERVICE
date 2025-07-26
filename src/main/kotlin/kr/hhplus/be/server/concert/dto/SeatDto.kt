package kr.hhplus.be.server.concert.dto

import kr.hhplus.be.server.concert.entity.Seat
import java.math.BigDecimal

/**
 * Seat Domain DTOs
 * 좌석 관련 DTO 클래스들
 */

/**
 * 좌석 기본 정보 DTO
 */
data class SeatDto(
    val seatId: Long,
    val scheduleId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val statusCode: String
) {
    companion object {
        fun from(seat: Seat): SeatDto {
            return SeatDto(
                seatId = seat.seatId,
                scheduleId = seat.scheduleId,
                seatNumber = seat.seatNumber,
                price = seat.price,
                statusCode = seat.status.code
            )
        }
    }
}

/**
 * 좌석 상태와 함께 조회하는 DTO (상태 정보 포함)
 */
data class SeatWithStatusDto(
    val seatId: Long,
    val scheduleId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val statusCode: String,
    val statusName: String?,
    val statusDescription: String?
) {
    companion object {
        fun from(seat: Seat, statusName: String?, statusDescription: String?): SeatWithStatusDto {
            return SeatWithStatusDto(
                seatId = seat.seatId,
                scheduleId = seat.scheduleId,
                seatNumber = seat.seatNumber,
                price = seat.price,
                statusCode = seat.status.code,
                statusName = statusName ?: seat.status.name,
                statusDescription = statusDescription ?: seat.status.description
            )
        }
    }
}

/**
 * 좌석 응답용 DTO
 */
data class SeatDetail(
    val seatId: Long,
    val scheduleId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val statusCode: String
) {
    companion object {
        fun from(data: SeatDto): SeatDetail {
            return SeatDetail(
                seatId = data.seatId,
                scheduleId = data.scheduleId,
                seatNumber = data.seatNumber,
                price = data.price,
                statusCode = data.statusCode
            )
        }
    }
}
