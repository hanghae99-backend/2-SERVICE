package kr.hhplus.be.server.concert.service

import kr.hhplus.be.server.concert.entity.SeatInfo
import kr.hhplus.be.server.concert.entity.SeatStatus
import kr.hhplus.be.server.concert.entity.ConcertNotFoundException
import kr.hhplus.be.server.concert.entity.SeatNotFoundException
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository
import org.springframework.stereotype.Service

@Service
class SeatService(
    private val seatJpaRepository: SeatJpaRepository,
    private val concertJpaRepository: ConcertJpaRepository,
    private val parameterValidator: ConcertParameterValidator
) {
    
    /**
     * 특정 콘서트의 예약 가능한 좌석 목록 조회
     */
    fun getAvailableSeats(concertId: Long): List<SeatInfo> {
        // 파라미터 검증
        parameterValidator.validateConcertId(concertId)
        
        // 콘서트 존재 확인
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
        // 파라미터 검증
        parameterValidator.validateConcertId(concertId)
        
        // 콘서트 존재 확인
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
        // 파라미터 검증
        parameterValidator.validateSeatId(seatId)
        
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
        // 파라미터 검증
        parameterValidator.validateSeatId(seatId)
        
        val seat = seatJpaRepository.findById(seatId).orElse(null)
            ?: throw SeatNotFoundException("좌석을 찾을 수 없습니다. ID: $seatId")
        
        return seat.status == SeatStatus.AVAILABLE
    }
    
    /**
     * 좌석 상태 변경
     */
    fun updateSeatStatus(seatId: Long, status: SeatStatus): Boolean {
        // 파라미터 검증
        parameterValidator.validateSeatId(seatId)
        
        val updatedCount = seatJpaRepository.updateSeatStatus(seatId, status)
        return updatedCount > 0
    }
}
