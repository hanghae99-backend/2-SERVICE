package kr.hhplus.be.server.api.reservation.usecase

import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.reservation.model.Reservation
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
    
    private val logger = LoggerFactory.getLogger(ReserveSeatUseCase::class.java)
    
    @LockGuard(
        keys = ["'seat:' + #seatId", "'user:reservation:' + #userId"],
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 12000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        logger.info("좌석 예약 시작 - userId: $userId, seatId: $seatId")
        
        validateToken(token)
        
        if (!seatService.isSeatAvailable(seatId)) {
            throw IllegalStateException("예약할 수 없는 좌석입니다: $seatId")
        }
        
        val reservation = reservationService.reserveSeat(userId, concertId, seatId)
        
        logger.info("좌석 예약 완료 - userId: $userId, reservationId: ${reservation.reservationId}")
        return reservation
    }
    
    private fun validateToken(token: String) {
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)
    }
}
