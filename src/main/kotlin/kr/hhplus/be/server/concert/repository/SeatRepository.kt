package kr.hhplus.be.server.concert.repository

import kr.hhplus.be.server.concert.entity.Seat
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
}
