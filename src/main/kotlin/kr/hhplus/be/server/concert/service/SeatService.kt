package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.entity.SeatInfo
import kr.hhplus.be.server.concert.entity.SeatStatus
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
    
    /**
     * 특정 콘서트의 예약 가능한 좌석 목록 조회
     */
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
    
    /**
     * 특정 콘서트의 모든 좌석 정보 조회 (상태 포함)
     */
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
    
    /**
     * 특정 좌석 정보 조회
     */
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
    
    /**
     * 좌석 예약 가능 여부 확인
     */
    fun isSeatAvailable(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
    }
    
    /**
     * 좌석 임시 예약
     */
    @Transactional
    fun reserveSeat(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val reservedSeat = seat.reserve() // 엔티티의 비즈니스 메소드 사용
        seatJpaRepository.save(reservedSeat)
        return true
    }
    
    /**
     * 좌석 예약 확정
     */
    @Transactional
    fun confirmSeat(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val confirmedSeat = seat.confirm() // 엔티티의 비즈니스 메소드 사용
        seatJpaRepository.save(confirmedSeat)
        return true
    }
    
    /**
     * 좌석 예약 해제
     */
    @Transactional
    fun releaseSeat(seatId: Long): Boolean {
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        val releasedSeat = seat.release() // 엔티티의 비즈니스 메소드 사용
        seatJpaRepository.save(releasedSeat)
        return true
    }
}
