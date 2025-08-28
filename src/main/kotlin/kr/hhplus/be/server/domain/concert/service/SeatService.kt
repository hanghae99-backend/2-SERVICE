package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.domain.concert.rules.ConcertBusinessRules
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
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

    fun getSeatLayout(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleRepository.findById(scheduleId)
            .orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        
        return seatRepository.findByScheduleId(scheduleId)
            .map { seat -> 
                SeatDto.from(seat).copy(
                    statusCode = "LAYOUT",
                )
            }
            .sortedBy { it.seatNumber }
    }

    fun getAllSeats(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleRepository.findById(scheduleId).orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        
        return seatRepository.findByScheduleId(scheduleId)
            .map { SeatDto.from(it) }
            .sortedBy { it.seatNumber }
    }

    fun getSeatById(seatId: Long): SeatDto {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException(seatId) }
        return SeatDto.from(seat)
    }

    fun isSeatAvailable(seatId: Long): Boolean {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException(seatId) }
        return seat.isAvailable()
    }

    fun validateSeatAvailability(seatId: Long) {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException(seatId) }
        if (!seat.isAvailable()) {
            throw IllegalStateException("예약할 수 없는 좌석입니다. ID: $seatId")
        }
    }
    @LockGuard(
        key = "'seat:' + #seatId",
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 3000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun reserveSeat(seatId: Long): SeatDto {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException(seatId) }
        
        if (!seat.isAvailable()) {
            throw IllegalStateException("이미 예약된 좌석입니다. ID: $seatId")
        }
        
        val reservedStatus = seatStatusTypeRepository.getReservedStatus()
        seat.reserve(reservedStatus)
        val savedSeat = seatRepository.save(seat)
        
        return SeatDto.from(savedSeat)
    }
    
    @LockGuard(
        key = "'seat:' + #seatId",
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 3000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun confirmSeat(seatId: Long): SeatDto {
        val seat = seatRepository.findById(seatId).orElseThrow { SeatNotFoundException(seatId) }
        val occupiedStatus = seatStatusTypeRepository.getOccupiedStatus()
        seat.confirm(occupiedStatus)
        val savedSeat = seatRepository.save(seat)
        
        return SeatDto.from(savedSeat)
    }

    @LockGuard(
        key = "'seat:' + #seatId",
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 3000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun releaseSeat(seatId: Long): SeatDto {
        val seat = seatRepository.findById(seatId)
            .orElseThrow { SeatNotFoundException(seatId) }
        
        if (!seat.isReserved()) {
            throw IllegalStateException("예약 상태가 아닌 좌석입니다. ID: $seatId")
        }
        
        val availableStatus = seatStatusTypeRepository.getAvailableStatus()
        seat.release(availableStatus)
        val savedSeat = seatRepository.save(seat)
        
        return SeatDto.from(savedSeat)
    }
}
