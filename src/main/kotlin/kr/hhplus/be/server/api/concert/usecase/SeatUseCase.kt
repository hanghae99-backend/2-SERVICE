package kr.hhplus.be.server.api.concert.usecase

import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockKeyManager
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.concert.event.SeatStatusChangedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class SeatUseCase(
    private val seatService: SeatService,
    private val distributedLock: DistributedLock,
    private val eventPublisher: DomainEventPublisher
) {
    
    /**
     * 특정 스케줄의 예약 가능한 좌석 목록 조회
     */
    fun getAvailableSeats(scheduleId: Long): List<SeatDto> {
        return seatService.getAvailableSeats(scheduleId)
    }
    
    /**
     * 특정 스케줄의 모든 좌석 목록 조회
     */
    fun getAllSeats(scheduleId: Long): List<SeatDto> {
        return seatService.getAllSeats(scheduleId)
    }
    
    /**
     * 좌석 상세 정보 조회
     */
    fun getSeatById(seatId: Long): SeatDto {
        return seatService.getSeatById(seatId)
    }
    
    /**
     * 좌석 예약 가능 여부 확인
     */
    fun isSeatAvailable(seatId: Long): Boolean {
        return seatService.isSeatAvailable(seatId)
    }

    /**
     * 좌석 번호 패턴으로 좌석 검색
     */
    fun getSeatsByNumberPattern(scheduleId: Long, pattern: String): List<SeatDto> {
        return seatService.getSeatsByNumberPattern(scheduleId, pattern)
    }

    @Transactional
    fun confirmSeat(seatId: Long): SeatDto {
        val lockKey = LockKeyManager.seatOperation(seatId)
        
        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 10000L,
            waitTimeoutMs = 5000L
        ) {
            val previousSeat = seatService.getSeatById(seatId)
            val result = seatService.confirmSeat(seatId)
            
            // 좌석 상태 변경 이벤트 발행
            val statusChangeEvent = SeatStatusChangedEvent(
                seatId = result.seatId,
                scheduleId = result.scheduleId,
                seatNumber = result.seatNumber,
                previousStatus = previousSeat.statusCode,
                newStatus = result.statusCode
            )
            eventPublisher.publish(statusChangeEvent)
            
            result
        }
    }
}