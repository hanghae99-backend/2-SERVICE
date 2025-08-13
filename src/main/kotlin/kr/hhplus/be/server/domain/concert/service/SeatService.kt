package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.lock.LockGuard
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
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

    @Cacheable(value = ["seats:available"], key = "#scheduleId")
    fun getAvailableSeats(scheduleId: Long): List<SeatDto> {
        val schedule = concertScheduleRepository.findById(scheduleId).orElseThrow { ConcertNotFoundException("콘서트 스케줄을 찾을 수 없습니다. ID: $scheduleId") }
        val availableStatus = seatStatusTypeRepository.getAvailableStatus()

        return seatRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(
            scheduleId, availableStatus.code
        ).map { SeatDto.from(it) }
    }

    @Cacheable(value = ["seats:layout"], key = "#scheduleId")
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

    @Cacheable(value = ["seats:all"], key = "#scheduleId")
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
    @LockGuard(key = "seat:#seatId")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Caching(evict = [
        CacheEvict(value = ["seats:available"], key = "#result.scheduleId"),
        CacheEvict(value = ["seats:all"], key = "#result.scheduleId")
    ])
    fun reserveSeat(seatId: Long): SeatDto {
        val seat = seatRepository.findByIdWithPessimisticLock(seatId).orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        
        if (!seat.isAvailable()) {
            throw IllegalStateException("이미 예약된 좌석입니다. ID: $seatId")
        }
        
        val reservedStatus = seatStatusTypeRepository.getReservedStatus()
        val reservedSeat = seat.reserve(reservedStatus)
        val savedSeat = seatRepository.save(reservedSeat)
        
        return SeatDto.from(savedSeat)
    }
    
    @LockGuard(key = "seat:#seatId")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Caching(evict = [
        CacheEvict(value = ["seats:available"], key = "#result.scheduleId"),
        CacheEvict(value = ["seats:all"], key = "#result.scheduleId")
    ])
    fun confirmSeat(seatId: Long): SeatDto {
        val seat = seatRepository.findByIdWithPessimisticLock(seatId).orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        val occupiedStatus = seatStatusTypeRepository.getOccupiedStatus()

        val confirmedSeat = seat.confirm(occupiedStatus)
        val savedSeat = seatRepository.save(confirmedSeat)
        
        return SeatDto.from(savedSeat)
    }

    @LockGuard(key = "seat:#seatId")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Caching(evict = [
        CacheEvict(value = ["seats:available"], key = "#result.scheduleId"),
        CacheEvict(value = ["seats:all"], key = "#result.scheduleId")
    ])
    fun releaseSeat(seatId: Long): SeatDto {
        val seat = seatRepository.findByIdWithPessimisticLock(seatId)
            .orElseThrow { SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId") }
        
        if (!seat.isReserved()) {
            throw IllegalStateException("예약 상태가 아닌 좌석입니다. ID: $seatId")
        }
        
        val availableStatus = seatStatusTypeRepository.getAvailableStatus()
        val releasedSeat = seat.release(availableStatus)
        val savedSeat = seatRepository.save(releasedSeat)
        
        return SeatDto.from(savedSeat)
    }
}
