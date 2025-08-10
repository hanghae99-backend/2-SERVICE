package kr.hhplus.be.server.api.reservation.usecase

import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.lock.LockGuard
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
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
    
    @LockGuard(key = "seat:#seatId")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)
        
        if (!seatService.isSeatAvailable(seatId)) {
            throw IllegalStateException("예약할 수 없는 좌석입니다: $seatId")
        }
        
        return try {
            reservationService.reserveSeat(userId, concertId, seatId)
        } catch (e: OptimisticLockingFailureException) {
            logger.warn("Optimistic lock failure during seat reservation. SeatId: $seatId, UserId: $userId")
            throw IllegalStateException("해당 좌석이 이미 다른 사용자에 의해 예약되었습니다. 다른 좌석을 선택해주세요.")
        }
    }
}
