package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.api.concert.dto.*
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.global.extension.orElseThrow
import org.springframework.cache.annotation.Cacheable
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
    
    @Cacheable(value = ["concerts:available"], key = "#startDate.toString() + ':' + #endDate.toString()")
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
    
    @Cacheable(value = ["concerts"], key = "#concertId")
    fun getConcertById(concertId: Long): ConcertDto {
        val concert = concertRepository.findById(concertId)
            .orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId") }
        
        return ConcertDto.from(concert)
    }
    
    @Cacheable(value = ["concerts:detail"], key = "#scheduleId")
    fun getConcertDetailByScheduleId(scheduleId: Long): ConcertDetailDto {
        val schedule = concertScheduleRepository.findById(scheduleId)
            .orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        
        val concert = concertRepository.findById(schedule.concertId)
            .orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: ${schedule.concertId}") }
        
        val seats = seatRepository.findByScheduleId(scheduleId)
        
        return ConcertDetailDto.from(concert, schedule, seats)
    }
    
    @Cacheable(value = ["schedules"], key = "#concertId")
    fun getSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto> {
        val concert = concertRepository.findById(concertId).orElseThrow { ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId") }
        
        val schedules = concertScheduleRepository.findByConcertId(concertId)
        
        return schedules.map { schedule ->
            ConcertWithScheduleDto.from(concert, schedule)
        }
    }
}
