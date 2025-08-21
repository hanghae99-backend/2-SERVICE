package kr.hhplus.be.server.domain.concert.repositories

import kr.hhplus.be.server.domain.concert.models.Seat

interface SeatRepository {
    fun save(seat: Seat): Seat
    fun saveAll(seats: List<Seat>): List<Seat> // 추가
    fun findById(id: Long): Seat?
    fun findByIdWithPessimisticLock(id: Long): Seat? // 추가
    fun findByScheduleId(scheduleId: Long): List<Seat>
    fun findByScheduleIdAndStatusCode(scheduleId: Long, statusCode: String): List<Seat>
    fun findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId: Long, statusCode: String): List<Seat> // 추가
    fun countByScheduleId(scheduleId: Long): Int // 추가
    fun findAll(): List<Seat>
    fun delete(seat: Seat)
    fun deleteAll()
    fun flush() // 추가
}
