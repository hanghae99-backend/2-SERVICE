package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.dto.*
import kr.hhplus.be.server.concert.repository.ConcertRepository
import kr.hhplus.be.server.concert.repository.ConcertScheduleRepository
import kr.hhplus.be.server.concert.repository.SeatRepository
import kr.hhplus.be.server.global.extension.orElseThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ConcertService(
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatRepository: SeatRepository
) {
    
    /**
     * 예약 가능한 콘서트 목록 조회 (기간별)
     */
    fun getAvailableConcerts(
        startDate: LocalDate = LocalDate.now(), 
        endDate: LocalDate = LocalDate.now().plusMonths(3)
    ): List<ConcertScheduleWithInfoDto> {
        val schedules = concertScheduleRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
            startDate, endDate, 0
        )
        
        if (schedules.isEmpty()) return emptyList()
        
        // 콘서트 정보를 한 번에 조회
        val concertIds = schedules.map { it.concertId }.distinct()
        val concerts = concertIds.mapNotNull { concertRepository.findById(it) }
        val concertMap = concerts.associateBy { it.concertId }
        
        return schedules.mapNotNull { schedule ->
            val concert = concertMap[schedule.concertId]
            if (concert != null) {
                ConcertScheduleWithInfoDto.from(concert, schedule)
            } else null
        }
    }
    
    /**
     * 특정 날짜의 콘서트 목록 조회
     */
    fun getConcertsByDate(date: LocalDate): List<ConcertScheduleWithInfoDto> {
        val schedules = concertScheduleRepository.findByConcertDate(date)
        
        if (schedules.isEmpty()) return emptyList()
        
        val concertIds = schedules.map { it.concertId }.distinct()
        val concerts = concertIds.mapNotNull { concertRepository.findById(it) }
        val concertMap = concerts.associateBy { it.concertId }
        
        return schedules.mapNotNull { schedule ->
            val concert = concertMap[schedule.concertId]
            if (concert != null) {
                ConcertScheduleWithInfoDto.from(concert, schedule)
            } else null
        }
    }
    
    /**
     * 콘서트 상세 정보 조회
     */
    fun getConcertById(concertId: Long): ConcertDto {
        val concert = concertRepository.findById(concertId)
            .orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId") }
        
        return ConcertDto.from(concert)
    }
    
    /**
     * 콘서트 스케줄 상세 정보 조회
     */
    fun getConcertScheduleById(scheduleId: Long): ConcertScheduleWithInfoDto {
        val schedule = concertScheduleRepository.findById(scheduleId)
            .orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        
        val concert = concertRepository.findById(schedule.concertId)
            .orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: ${schedule.concertId}") }
            
        return ConcertScheduleWithInfoDto.from(concert, schedule)
    }
    
    /**
     * 콘서트 상세 정보 조회 (스케줄과 좌석 정보 포함)
     */
    fun getConcertDetailByScheduleId(scheduleId: Long): ConcertDetailDto {
        val schedule = concertScheduleRepository.findById(scheduleId)
            .orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        
        val concert = concertRepository.findById(schedule.concertId)
            .orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: ${schedule.concertId}") }
        
        val seats = seatRepository.findByScheduleId(scheduleId)
        
        return ConcertDetailDto.from(concert, schedule, seats)
    }
    

    
    /**
     * 특정 콘서트의 모든 스케줄 조회
     */
    fun getSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto> {
        val concert = concertRepository.findById(concertId).orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId") }
        
        val schedules = concertScheduleRepository.findByConcertId(concertId)
        
        return schedules.map { schedule ->
            ConcertWithScheduleDto.from(concert, schedule)
        }
    }
    
    /**
     * 특정 콘서트의 예약 가능한 스케줄 조회
     */
    fun getAvailableSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto> {
        val concert = concertRepository.findById(concertId).orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId")}
        
        val schedules = concertScheduleRepository.findByConcertIdAndAvailableSeatsGreaterThanAndConcertDateGreaterThanEqualOrderByConcertDateAsc(
            concertId, 0, LocalDate.now()
        )
        
        return schedules.map { schedule ->
            ConcertWithScheduleDto.from(concert, schedule)
        }
    }
}
