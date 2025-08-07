package kr.hhplus.be.server.domain.concert.infrastructure

import kr.hhplus.be.server.domain.concert.models.Seat
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal

interface SeatJpaRepository : JpaRepository<Seat, Long> {
    
    // 스케줄 ID로 좌석 조회
    fun findByScheduleId(scheduleId: Long): List<Seat>
    
    // 스케줄별 좌석 총 개수
    fun countByScheduleId(scheduleId: Long): Int
    
    // 상태 코드로 조회
    fun findByScheduleIdAndStatusCode(scheduleId: Long, statusCode: String): List<Seat>
    
    // 좌석 번호로 조회
    fun findByScheduleIdAndSeatNumber(scheduleId: Long, seatNumber: String): Seat?
    
    // 예약 가능한 좌석 조회 (좌석 번호 순 정렬)
    fun findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId: Long, statusCode: String): List<Seat>
    
    // 예약 가능한 좌석 개수 조회
    fun countByScheduleIdAndStatusCode(scheduleId: Long, statusCode: String): Int
    
    // 가격 범위로 좌석 조회
    fun findByScheduleIdAndStatusCodeAndPriceBetweenOrderByPriceAscSeatNumberAsc(
        scheduleId: Long, 
        statusCode: String, 
        minPrice: BigDecimal, 
        maxPrice: BigDecimal
    ): List<Seat>
    
    // 좌석 번호 패턴으로 검색
    fun findByScheduleIdAndSeatNumberContainingOrderBySeatNumberAsc(scheduleId: Long, seatNumberPattern: String): List<Seat>
    
    // 특정 가격 이하의 좌석 조회
    fun findByScheduleIdAndStatusCodeAndPriceLessThanEqualOrderByPriceAsc(
        scheduleId: Long, 
        statusCode: String, 
        maxPrice: BigDecimal
    ): List<Seat>
}
