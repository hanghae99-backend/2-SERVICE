package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.lock.LockGuard
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SeatService(
    private val seatRepository: SeatRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatStatusTypeRepository: SeatStatusTypePojoRepository
) {

    fun getAvailableSeats(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleRepository.findById(scheduleId).orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        val availableStatus = seatStatusTypeRepository.getAvailableStatus()

        return seatRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(
            scheduleId, availableStatus.code
        ).map { SeatDto.from(it) }
    }

    fun getAllSeats(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleRepository.findById(scheduleId).orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        
        return seatRepository.findByScheduleId(scheduleId)
            .map { SeatDto.from(it) }
            .sortedBy { it.seatNumber }
    }

    fun getSeatById(seatId: Long): SeatDto {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        return SeatDto.from(seat)
    }

    fun isSeatAvailable(seatId: Long): Boolean {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        return seat.isAvailable()
    }

    fun getSeatsByNumberPattern(scheduleId: Long, pattern: String): List<SeatDto> {
        return seatRepository.findByScheduleIdAndSeatNumberContainingOrderBySeatNumberAsc(
            scheduleId, pattern
        ).map { SeatDto.from(it) }
    }

    @Transactional
    @LockGuard(key = "seat:#seatId")
    fun confirmSeat(seatId: Long): SeatDto {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        val occupiedStatus = seatStatusTypeRepository.getOccupiedStatus()

        val confirmedSeat = seat.confirm(occupiedStatus)
        val savedSeat = seatRepository.save(confirmedSeat)
        
        return SeatDto.from(savedSeat)
    }
    
    @Transactional
    fun confirmSeatInternal(seatId: Long): SeatDto {
        return confirmSeat(seatId)
    }
}
