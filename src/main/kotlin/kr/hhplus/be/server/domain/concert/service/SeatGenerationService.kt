package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 콘서트 스케줄 생성 시 좌석을 자동으로 생성하는 서비스
 */
@Service
@Transactional
class SeatGenerationService(
    private val seatRepository: SeatRepository,
    private val seatStatusTypeRepository: SeatStatusTypePojoRepository
) {
    
    companion object {
        private const val TOTAL_SEATS = 50
        private const val SEAT_PRICE = "100000" // 10만원 통일
    }
    
    /**
     * 콘서트 스케줄에 대한 좌석을 자동 생성합니다 (50개 고정)
     */
    fun generateSeatsForSchedule(schedule: ConcertSchedule): List<Seat> {
        val availableStatus = seatStatusTypeRepository.getAvailableStatus()
        
        // 50개 좌석 생성 (등급 없이 통일)
        val seats = (1..TOTAL_SEATS).map { i ->
            Seat.create(
                scheduleId = schedule.scheduleId,
                seatNumber = String.format("%02d", i), // 01, 02, ..., 50
                price = BigDecimal(SEAT_PRICE),
                availableStatus = availableStatus
            )
        }
        
        return seatRepository.saveAll(seats)
    }
    
    /**
     * 기존 좌석이 있는지 확인
     */
    fun hasExistingSeats(scheduleId: Long): Boolean {
        return seatRepository.countByScheduleId(scheduleId) > 0
    }
    
    /**
     * 좌석 수 확인
     */
    fun getSeatCount(scheduleId: Long): Int {
        return seatRepository.countByScheduleId(scheduleId)
    }
}