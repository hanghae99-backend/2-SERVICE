package kr.hhplus.be.server.domain.concert.repositories

import kr.hhplus.be.server.domain.concert.models.Seat
import java.math.BigDecimal

interface SeatRepository {
    fun save(seat: Seat): Seat
    fun saveAll(seats: List<Seat>): List<Seat>
    fun findById(id: Long): Seat?
    fun findByScheduleId(scheduleId: Long): List<Seat>
    fun findByScheduleIdAndStatusCode(scheduleId: Long, statusCode: String): List<Seat>
    fun findByScheduleIdAndSeatNumber(scheduleId: Long, seatNumber: String): Seat?
    fun findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId: Long, statusCode: String): List<Seat>
    fun countByScheduleId(scheduleId: Long): Int
    fun countByScheduleIdAndStatusCode(scheduleId: Long, statusCode: String): Int
    fun findByScheduleIdAndStatusCodeAndPriceBetweenOrderByPriceAscSeatNumberAsc(
        scheduleId: Long, 
        statusCode: String, 
        minPrice: BigDecimal, 
        maxPrice: BigDecimal
    ): List<Seat>
    fun findByScheduleIdAndSeatNumberContainingOrderBySeatNumberAsc(scheduleId: Long, seatNumberPattern: String): List<Seat>
    fun findByScheduleIdAndStatusCodeAndPriceLessThanEqualOrderByPriceAsc(
        scheduleId: Long, 
        statusCode: String, 
        maxPrice: BigDecimal
    ): List<Seat>
    fun delete(seat: Seat)
    fun deleteAll() // 테스트용 - 모든 좌석 데이터 삭제
}
