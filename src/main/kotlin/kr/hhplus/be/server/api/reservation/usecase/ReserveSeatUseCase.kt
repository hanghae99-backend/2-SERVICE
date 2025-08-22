package kr.hhplus.be.server.api.reservation.usecase

import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.exception.ReservationFailedException
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ReserveSeatUseCase(
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(ReserveSeatUseCase::class.java)
    }
    
    @LockGuard(
        keys = ["'seat:' + #seatId", "'user:reservation:' + #userId"],
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 12000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        logger.info("좌석 예약 시작 - userId: {}, concertId: {}, seatId: {}", userId, concertId, seatId)
        
        try {
            // 토큰 유효성 검증
            validateToken(token)
            
            // 좌석 가용성 사전 체크
            validateSeatAvailability(seatId)
            
            // 예약 생성
            val reservation = reservationService.reserveSeat(userId, concertId, seatId)
            
            logger.info(
                "좌석 예약 완료 - userId: {}, reservationId: {}, seatId: {}, expiresAt: {}", 
                userId, reservation.reservationId, seatId, reservation.expiresAt
            )
            
            return reservation
            
        } catch (e: Exception) {
            logger.error(
                "좌석 예약 실패 - userId: {}, seatId: {}, error: {}", 
                userId, seatId, e.message, e
            )
            throw ReservationFailedException("좌석 예약에 실패했습니다: ${e.message}", e)
        }
    }
    
    private fun validateToken(token: String) {
        logger.debug("토큰 유효성 검증 시작 - token: {}", token)
        
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)
        
        logger.debug("토큰 유효성 검증 완료 - token: {}, status: {}", token, status)
    }
    
    private fun validateSeatAvailability(seatId: Long) {
        logger.debug("좌석 가용성 검증 시작 - seatId: {}", seatId)
        
        val seat = seatService.getSeatById(seatId)
        
        if (seat.statusCode != SeatStatusType.AVAILABLE) {
            logger.warn("예약 불가능한 좌석 - seatId: {}, status: {}", seatId, seat.statusCode)
            throw ReservationFailedException("예약할 수 없는 좌석입니다: $seatId")
        }
        
        logger.debug("좌석 가용성 검증 완료 - seatId: {}", seatId)
    }
}
