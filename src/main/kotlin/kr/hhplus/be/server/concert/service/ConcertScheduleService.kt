package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.dto.request.ConcertScheduleCreateRequest
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.repository.ConcertRepository
import kr.hhplus.be.server.concert.repository.ConcertScheduleRepository
import kr.hhplus.be.server.global.exception.ParameterValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 콘서트 스케줄 관리 서비스
 */
@Service
@Transactional
class ConcertScheduleService(
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatGenerationService: SeatGenerationService
) {
    
    /**
     * 콘서트 스케줄 생성 (좌석 자동 생성 포함 - 50개 고정)
     */
    fun createConcertSchedule(request: ConcertScheduleCreateRequest): ConcertSchedule {
        // 1. 콘서트 존재 확인
        val concert = concertRepository.findById(request.concertId)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다: ${request.concertId}")
        
        // 2. 스케줄 생성 (50개 고정)
        val schedule = ConcertSchedule.create(
            concertId = request.concertId,
            concertDate = request.concertDate,
            venue = request.venue,
            totalSeats = 50 // 고정
        )
        
        val savedSchedule = concertScheduleRepository.save(schedule)
        
        // 3. 좌석 자동 생성 (50개)
        if (!seatGenerationService.hasExistingSeats(savedSchedule.scheduleId)) {
            val generatedSeats = seatGenerationService.generateSeatsForSchedule(savedSchedule)
            
            // 실제 생성된 좌석 수와 일치하는지 확인 (50개)
            if (generatedSeats.size != 50) {
                throw ParameterValidationException(
                    "좌석 생성 실패: 예상 50개, 생성 ${generatedSeats.size}개"
                )
            }
        }
        
        return savedSchedule
    }
    
    /**
     * 기존 스케줄에 좌석 추가 생성
     */
    fun generateSeatsForExistingSchedule(scheduleId: Long): List<kr.hhplus.be.server.concert.entity.Seat> {
        val schedule = concertScheduleRepository.findById(scheduleId)
            ?: throw ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다: $scheduleId")
        
        if (seatGenerationService.hasExistingSeats(scheduleId)) {
            throw IllegalStateException("이미 좌석이 생성된 스케줄입니다: $scheduleId")
        }
        
        return seatGenerationService.generateSeatsForSchedule(schedule)
    }
    
    /**
     * 스케줄 삭제 (좌석도 함께 삭제됨 - CASCADE)
     */
    fun deleteSchedule(scheduleId: Long) {
        val schedule = concertScheduleRepository.findById(scheduleId)
            ?: throw ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다: $scheduleId")
        
        // 예약된 좌석이 있는지 확인
        val seatCount = seatGenerationService.getSeatCount(scheduleId)
        if (seatCount > 0) {
            throw IllegalStateException("좌석이 존재하는 스케줄은 삭제할 수 없습니다. 좌석을 먼저 정리해주세요.")
        }
        
        concertScheduleRepository.delete(schedule)
    }
}