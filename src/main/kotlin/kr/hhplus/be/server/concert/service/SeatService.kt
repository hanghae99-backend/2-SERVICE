package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.dto.SeatDto
import kr.hhplus.be.server.concert.entity.SeatStatusType
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.concert.repository.ConcertScheduleJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class SeatService(
    private val seatJpaRepository: SeatJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
) {
    
    /**
     * 특정 스케줄의 예약 가능한 좌석 목록 조회
     */
    fun getAvailableSeats(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleJpaRepository.findById(scheduleId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId")
        
        return seatJpaRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(
            scheduleId, SeatStatusType.AVAILABLE
        ).map { SeatDto.from(it) }
    }
    
    /**
     * 특정 스케줄의 모든 좌석 목록 조회
     */
    fun getAllSeats(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleJpaRepository.findById(scheduleId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId")
        
        return seatJpaRepository.findByScheduleId(scheduleId)
            .map { SeatDto.from(it) }
            .sortedBy { it.seatNumber }
    }
    
    /**
     * 좌석 상세 정보 조회
     */
    fun getSeatById(seatId: Long): SeatDto {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        return SeatDto.from(seat)
    }
    
    /**
     * 좌석 예약 가능 여부 확인
     */
    fun isSeatAvailable(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        return seat.isAvailable()
    }
    
    /**
     * 가격 범위로 예약 가능한 좌석 조회
     */
    fun getAvailableSeatsByPriceRange(scheduleId: Long, minPrice: BigDecimal, maxPrice: BigDecimal): List<SeatDto> {
        return seatJpaRepository.findByScheduleIdAndStatusCodeAndPriceBetweenOrderByPriceAscSeatNumberAsc(
            scheduleId, SeatStatusType.AVAILABLE, minPrice, maxPrice
        ).map { SeatDto.from(it) }
    }
    
    /**
     * 특정 가격 이하의 예약 가능한 좌석 조회
     */
    fun getAvailableSeatsUnderPrice(scheduleId: Long, maxPrice: BigDecimal): List<SeatDto> {
        return seatJpaRepository.findByScheduleIdAndStatusCodeAndPriceLessThanEqualOrderByPriceAsc(
            scheduleId, SeatStatusType.AVAILABLE, maxPrice
        ).map { SeatDto.from(it) }
    }
    
    /**
     * 좌석 번호 패턴으로 좌석 검색
     */
    fun getSeatsByNumberPattern(scheduleId: Long, pattern: String): List<SeatDto> {
        return seatJpaRepository.findByScheduleIdAndSeatNumberContainingOrderBySeatNumberAsc(
            scheduleId, pattern
        ).map { SeatDto.from(it) }
    }
    

    /**
     * 예약 가능한 좌석 개수 조회
     */
    fun countAvailableSeats(scheduleId: Long): Int {
        return seatJpaRepository.countByScheduleIdAndStatusCode(scheduleId, SeatStatusType.AVAILABLE)
    }
    
    @Transactional
    fun reserveSeat(seatId: Long): SeatDto {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val reservedSeat = seat.reserve()
        val savedSeat = seatJpaRepository.save(reservedSeat)
        return SeatDto.from(savedSeat)
    }
    
    @Transactional
    fun confirmSeat(seatId: Long): SeatDto {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val confirmedSeat = seat.confirm()
        val savedSeat = seatJpaRepository.save(confirmedSeat)
        return SeatDto.from(savedSeat)
    }
    
    @Transactional
    fun releaseSeat(seatId: Long): SeatDto {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val releasedSeat = seat.release()
        val savedSeat = seatJpaRepository.save(releasedSeat)
        return SeatDto.from(savedSeat)
    }
}
