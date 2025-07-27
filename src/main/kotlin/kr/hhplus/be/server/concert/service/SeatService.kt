package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.dto.SeatDto
import kr.hhplus.be.server.concert.entity.SeatStatusType
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.concert.repository.ConcertScheduleRepository
import kr.hhplus.be.server.concert.repository.SeatRepository
import kr.hhplus.be.server.concert.repository.SeatStatusTypePojoRepository
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockKeyManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SeatService(
    private val seatRepository: SeatRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatStatusTypeRepository: SeatStatusTypePojoRepository,
    private val distributedLock: DistributedLock
) {
    
    /**
     * 특정 스케줄의 예약 가능한 좌석 목록 조회
     */
    fun getAvailableSeats(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleRepository.findById(scheduleId).orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        val availableStatus = seatStatusTypeRepository.getAvailableStatus()

        return seatRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(
            scheduleId, availableStatus.code
        ).map { SeatDto.from(it) }
    }
    
    /**
     * 특정 스케줄의 모든 좌석 목록 조회
     */
    fun getAllSeats(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleRepository.findById(scheduleId).orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        
        return seatRepository.findByScheduleId(scheduleId)
            .map { SeatDto.from(it) }
            .sortedBy { it.seatNumber }
    }
    
    /**
     * 좌석 상세 정보 조회
     */
    fun getSeatById(seatId: Long): SeatDto {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        return SeatDto.from(seat)
    }
    
    /**
     * 좌석 예약 가능 여부 확인
     */
    fun isSeatAvailable(seatId: Long): Boolean {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        
        return seat.isAvailable()
    }

    /**
     * 좌석 번호 패턴으로 좌석 검색
     */
    fun getSeatsByNumberPattern(scheduleId: Long, pattern: String): List<SeatDto> {
        return seatRepository.findByScheduleIdAndSeatNumberContainingOrderBySeatNumberAsc(
            scheduleId, pattern
        ).map { SeatDto.from(it) }
    }

    @Transactional
    fun confirmSeat(seatId: Long): SeatDto {
        val lockKey = LockKeyManager.seatOperation(seatId)
        
        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 10000L,
            waitTimeoutMs = 5000L
        ) {
            confirmSeatInternal(seatId)
        }
    }
    
    /**
     * PaymentService에서 내부 호출용 (중첩 락 방지)
     */
    @Transactional
    fun confirmSeatInternal(seatId: Long): SeatDto {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        val occupiedStatus = seatStatusTypeRepository.getOccupiedStatus()

        val confirmedSeat = seat.confirm(occupiedStatus)
        val savedSeat = seatRepository.save(confirmedSeat)
        return SeatDto.from(savedSeat)
    }

}
