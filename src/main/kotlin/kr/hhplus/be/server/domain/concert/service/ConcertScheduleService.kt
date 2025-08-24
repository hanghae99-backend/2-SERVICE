package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.api.concert.dto.request.ConcertScheduleCreateRequest
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.common.ConcertBusinessRules
import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.global.properties.ConcertProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ConcertScheduleService(
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatGenerationService: SeatGenerationService,
    private val concertProperties: ConcertProperties,
    private val concertBusinessRules: ConcertBusinessRules
) {
    
    fun createConcertSchedule(request: ConcertScheduleCreateRequest): ConcertSchedule {
        val expectedSeats = concertProperties.defaultSeatsPerSchedule
        
        val concert = concertRepository.findById(request.concertId)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다: ${request.concertId}")
        
        concertBusinessRules.validateSeatCount(expectedSeats)
        concertBusinessRules.validateScheduleTime(request.concertDate.atStartOfDay())
        
        val schedule = ConcertSchedule.create(
            concertId = request.concertId,
            concertDate = request.concertDate,
            venue = request.venue,
            totalSeats = expectedSeats
        )
        
        val savedSchedule = concertScheduleRepository.save(schedule)
        
        if (!seatGenerationService.hasExistingSeats(savedSchedule.scheduleId)) {
            val generatedSeats = seatGenerationService.generateSeatsForSchedule(savedSchedule)
            
            if (generatedSeats.size != expectedSeats) {
                throw ParameterValidationException(
                    "좌석 생성 실패: 예상 ${expectedSeats}개, 생성 ${generatedSeats.size}개"
                )
            }
        }
        
        return savedSchedule
    }
    
    fun generateSeatsForExistingSchedule(scheduleId: Long): List<Seat> {
        val schedule = concertScheduleRepository.findById(scheduleId)
            ?: throw ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다: $scheduleId")
        
        if (seatGenerationService.hasExistingSeats(scheduleId)) {
            throw IllegalStateException("이미 좌석이 생성된 스케줄입니다: $scheduleId")
        }
        
        return seatGenerationService.generateSeatsForSchedule(schedule)
    }
    
    fun deleteSchedule(scheduleId: Long) {
        val schedule = concertScheduleRepository.findById(scheduleId)
            ?: throw ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다: $scheduleId")
        
        val seatCount = seatGenerationService.getSeatCount(scheduleId)
        if (seatCount > 0) {
            throw IllegalStateException("좌석이 존재하는 스케줄은 삭제할 수 없습니다. 좌석을 먼저 정리해주세요.")
        }
        
        concertScheduleRepository.delete(schedule)
    }
}