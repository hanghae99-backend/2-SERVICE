package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.entity.Concert
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.concert.entity.ConcertNotFoundException
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ConcertService(
    private val concertJpaRepository: ConcertJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val parameterValidator: ConcertParameterValidator
) {
    
    /**
     * 예약 가능한 콘서트 목록 조회 (기간별)
     */
    fun getAvailableConcerts(
        startDate: LocalDate = LocalDate.now(), 
        endDate: LocalDate = LocalDate.now().plusMonths(3)
    ): List<ConcertSchedule> {
        // 파라미터 검증
        parameterValidator.validateConcertDate(startDate)
        parameterValidator.validateConcertDate(endDate)
        
        return concertJpaRepository.findAvailableConcertsByDateRange(startDate, endDate)
            .map { concert ->
                val availableSeats = concertJpaRepository.countAvailableSeatsByConcertId(concert.concertId)
                ConcertSchedule(
                    concertId = concert.concertId,
                    title = concert.title,
                    artist = concert.artist,
                    venue = concert.venue,
                    concertDate = concert.concertDate,
                    startTime = concert.startTime,
                    endTime = concert.endTime,
                    basePrice = concert.basePrice,
                    availableSeats = availableSeats
                )
            }
    }
    
    /**
     * 특정 날짜의 콘서트 목록 조회
     */
    fun getConcertsByDate(date: LocalDate): List<ConcertSchedule> {
        // 파라미터 검증
        parameterValidator.validateConcertDate(date)
        
        return concertJpaRepository.findByConcertDate(date)
            .map { concert ->
                val availableSeats = concertJpaRepository.countAvailableSeatsByConcertId(concert.concertId)
                ConcertSchedule(
                    concertId = concert.concertId,
                    title = concert.title,
                    artist = concert.artist,
                    venue = concert.venue,
                    concertDate = concert.concertDate,
                    startTime = concert.startTime,
                    endTime = concert.endTime,
                    basePrice = concert.basePrice,
                    availableSeats = availableSeats
                )
            }
    }
    
    /**
     * 콘서트 상세 정보 조회
     */
    fun getConcertById(concertId: Long): ConcertSchedule {
        // 파라미터 검증
        parameterValidator.validateConcertId(concertId)
        
        val concert = concertJpaRepository.findById(concertId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId")
        
        val availableSeats = concertJpaRepository.countAvailableSeatsByConcertId(concertId)
        
        return ConcertSchedule(
            concertId = concert.concertId,
            title = concert.title,
            artist = concert.artist,
            venue = concert.venue,
            concertDate = concert.concertDate,
            startTime = concert.startTime,
            endTime = concert.endTime,
            basePrice = concert.basePrice,
            availableSeats = availableSeats
        )
    }
    
    /**
     * 콘서트 엔티티 조회 (내부 사용)
     */
    fun getConcertEntityById(concertId: Long): Concert? {
        // 파라미터 검증
        parameterValidator.validateConcertId(concertId)
        
        return concertJpaRepository.findById(concertId).orElse(null)
    }
}
