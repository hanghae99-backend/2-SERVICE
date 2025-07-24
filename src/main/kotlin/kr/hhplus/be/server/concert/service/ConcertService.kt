package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.entity.Concert
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.concert.entity.SeatStatusType
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.dto.*
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ConcertService(
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository
) {
    
    /**
     * 예약 가능한 콘서트 목록 조회 (기간별)
     */
    fun getAvailableConcerts(
        startDate: LocalDate = LocalDate.now(), 
        endDate: LocalDate = LocalDate.now().plusMonths(3)
    ): List<ConcertScheduleWithInfoDto> {
        val schedules = concertScheduleJpaRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
            startDate, endDate, 0
        )
        
        if (schedules.isEmpty()) return emptyList()
        
        // 콘서트 정보를 한 번에 조회
        val concertIds = schedules.map { it.concertId }.distinct()
        val concertMap = concertJpaRepository.findAllById(concertIds)
            .associateBy { it.concertId }
        
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
        val schedules = concertScheduleJpaRepository.findByConcertDate(date)
        
        if (schedules.isEmpty()) return emptyList()
        
        val concertIds = schedules.map { it.concertId }.distinct()
        val concertMap = concertJpaRepository.findAllById(concertIds)
            .associateBy { it.concertId }
        
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
        val concert = concertJpaRepository.findById(concertId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId")
        
        return ConcertDto.from(concert)
    }
    
    /**
     * 콘서트 스케줄 상세 정보 조회
     */
    fun getConcertScheduleById(scheduleId: Long): ConcertScheduleWithInfoDto {
        val schedule = concertScheduleJpaRepository.findById(scheduleId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId")
        
        val concert = concertJpaRepository.findById(schedule.concertId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: ${schedule.concertId}")
            
        return ConcertScheduleWithInfoDto.from(concert, schedule)
    }
    
    /**
     * 콘서트 상세 정보 조회 (스케줄과 좌석 정보 포함)
     */
    fun getConcertDetailByScheduleId(scheduleId: Long): ConcertDetailDto {
        val schedule = concertScheduleJpaRepository.findById(scheduleId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId")
        
        val concert = concertJpaRepository.findById(schedule.concertId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: ${schedule.concertId}")
        
        val seats = seatJpaRepository.findByScheduleId(scheduleId)
        
        return ConcertDetailDto.from(concert, schedule, seats)
    }
    
    /**
     * 특정 스케줄의 좌석 목록 조회
     */
    fun getSeatsByScheduleId(scheduleId: Long): List<SeatDto> {
        val seats = seatJpaRepository.findByScheduleId(scheduleId)
        return seats.map { SeatDto.from(it) }
    }
    
    /**
     * 특정 스케줄의 예약 가능한 좌석 목록 조회
     */
    fun getAvailableSeatsByScheduleId(scheduleId: Long): List<SeatDto> {
        val seats = seatJpaRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(
            scheduleId, SeatStatusType.AVAILABLE
        )
        return seats.map { SeatDto.from(it) }
    }
    
    /**
     * 특정 콘서트의 모든 스케줄 조회
     */
    fun getSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto> {
        val concert = concertJpaRepository.findById(concertId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId")
        
        val schedules = concertScheduleJpaRepository.findByConcertId(concertId)
        
        return schedules.map { schedule ->
            ConcertWithScheduleDto.from(concert, schedule)
        }
    }
    
    /**
     * 특정 콘서트의 예약 가능한 스케줄 조회
     */
    fun getAvailableSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto> {
        val concert = concertJpaRepository.findById(concertId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId")
        
        val schedules = concertScheduleJpaRepository.findByConcertIdAndAvailableSeatsGreaterThanAndConcertDateGreaterThanEqualOrderByConcertDateAsc(
            concertId, 0, LocalDate.now()
        )
        
        return schedules.map { schedule ->
            ConcertWithScheduleDto.from(concert, schedule)
        }
    }
    
    /**
     * 아티스트별 콘서트 검색
     */
    fun getConcertsByArtist(artist: String): List<ConcertDto> {
        val concerts = concertJpaRepository.findByArtist(artist)
        return concerts.map { ConcertDto.from(it) }
    }
    
    /**
     * 키워드로 콘서트 검색
     */
    fun searchConcerts(keyword: String): List<ConcertDto> {
        val concerts = concertJpaRepository.findByTitleContainingOrArtistContaining(keyword, keyword)
        return concerts.map { ConcertDto.from(it) }
    }
    
    /**
     * 콘서트 엔티티 조회 (내부 사용)
     */
    fun getConcertEntityById(concertId: Long): Concert? {
        return concertJpaRepository.findById(concertId).orElse(null)
    }
    
    /**
     * 콘서트 스케줄 엔티티 조회 (내부 사용)
     */
    fun getConcertScheduleEntityById(scheduleId: Long): ConcertSchedule? {
        return concertScheduleJpaRepository.findById(scheduleId).orElse(null)
    }
}
