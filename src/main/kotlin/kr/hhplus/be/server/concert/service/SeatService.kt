package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.entity.SeatInfo
import kr.hhplus.be.server.concert.entity.ConcertNotFoundException
import kr.hhplus.be.server.concert.entity.SeatNotFoundException
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SeatService(
    private val seatJpaRepository: SeatJpaRepository,
    private val concertJpaRepository: ConcertJpaRepository,
) {
    
    fun getAvailableSeats(concertId: Long): List<SeatInfo> {
        val concert = concertJpaRepository.findById(concertId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId")
        
        return seatJpaRepository.findAvailableSeatsByConcertId(concertId)
            .map { seat ->
                SeatInfo(
                    seatId = seat.seatId,
                    seatNumber = seat.seatNumber,
                    price = seat.price,
                    status = seat.status
                )
            }
    }
    
    fun getAllSeats(concertId: Long): List<SeatInfo> {
        val concert = concertJpaRepository.findById(concertId).orElse(null)
            ?: throw ConcertNotFoundException("콘서트를 찾을 수 없습니다. ID: $concertId")
        
        return seatJpaRepository.findByConcertId(concertId)
            .map { seat ->
                SeatInfo(
                    seatId = seat.seatId,
                    seatNumber = seat.seatNumber,
                    price = seat.price,
                    status = seat.status
                )
            }
            .sortedBy { it.seatNumber }
    }
    
    fun getSeatById(seatId: Long): SeatInfo {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        return SeatInfo(
            seatId = seat.seatId,
            seatNumber = seat.seatNumber,
            price = seat.price,
            status = seat.status
        )
    }
    
    fun isSeatAvailable(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        return seat.isAvailable()
    }
    
    @Transactional
    fun reserveSeat(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val reservedSeat = seat.reserve()
        seatJpaRepository.save(reservedSeat)
        return true
    }
    
    @Transactional
    fun confirmSeat(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val confirmedSeat = seat.confirm()
        seatJpaRepository.save(confirmedSeat)
        return true
    }
    
    @Transactional
    fun releaseSeat(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val releasedSeat = seat.release()
        seatJpaRepository.save(releasedSeat)
        return true
    }
}
