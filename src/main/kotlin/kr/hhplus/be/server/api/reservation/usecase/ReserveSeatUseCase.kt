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
        key = "seat:#seatId",
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 10000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)
        
        if (!seatService.isSeatAvailable(seatId)) {
            throw IllegalStateException("예약할 수 없는 좌석입니다: $seatId")
        }
        
        return reservationService.reserveSeat(userId, concertId, seatId)
    }
}
