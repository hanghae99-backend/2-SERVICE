package kr.hhplus.be.server.api.reservation.usecase

import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.exception.ReservationCancelFailedException
import kr.hhplus.be.server.domain.reservation.exception.ReservationAccessDeniedException
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class CancelReservationUseCase(
    private val reservationService: ReservationService,
    private val tokenDomainService: TokenDomainService,
    private val tokenLifecycleManager: TokenLifecycleManager
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(CancelReservationUseCase::class.java)
    }
    
    @LockGuard(
        key = "'reservation:' + #reservationId",
        strategy = LockStrategy.SIMPLE,
        waitTimeoutMs = 5000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(reservationId: Long, userId: Long, cancelReason: String?, token: String): Reservation {
        logger.info(
            "예약 취소 시작 - reservationId: {}, userId: {}, reason: {}", 
            reservationId, userId, cancelReason
        )
        
        try {
            // 토큰 유효성 검증
            validateToken(token)
            
            // 예약 권한 검증
            validateReservationOwnership(reservationId, userId)
            
            // 예약 취소 처리
            val cancelledReservation = reservationService.cancelReservation(
                reservationId = reservationId,
                userId = userId,
                cancelReason = cancelReason ?: "사용자 요청"
            )
            
            logger.info(
                "예약 취소 완료 - reservationId: {}, userId: {}, status: {}", 
                reservationId, userId, cancelledReservation.status.code
            )
            
            return cancelledReservation
            
        } catch (e: Exception) {
            logger.error(
                "예약 취소 실패 - reservationId: {}, userId: {}, error: {}", 
                reservationId, userId, e.message, e
            )
            throw ReservationCancelFailedException("예약 취소에 실패했습니다: ${e.message}", e)
        }
    }
    
    private fun validateToken(token: String) {
        logger.debug("토큰 유효성 검증 시작 - token: {}", token)
        
        val waitingToken = tokenLifecycleManager.findToken(token)
        val status = tokenLifecycleManager.getTokenStatus(token)
        tokenDomainService.validateActiveToken(waitingToken, status)
        
        logger.debug("토큰 유효성 검증 완료 - token: {}, status: {}", token, status)
    }
    
    private fun validateReservationOwnership(reservationId: Long, userId: Long) {
        logger.debug("예약 소유권 검증 시작 - reservationId: {}, userId: {}", reservationId, userId)
        
        val reservation = reservationService.getReservationById(reservationId)
        
        if (reservation.userId != userId) {
            logger.warn(
                "예약 소유권 불일치 - reservationId: {}, requestUserId: {}, ownerUserId: {}", 
                reservationId, userId, reservation.userId
            )
            throw ReservationAccessDeniedException(userId, reservationId)
        }
        
        if (reservation.status.code == "CANCELLED") {
            logger.warn(
                "이미 취소된 예약 - reservationId: {}, status: {}", 
                reservationId, reservation.status.code
            )
            throw ReservationCancelFailedException("이미 취소된 예약입니다")
        }
        
        logger.debug("예약 소유권 검증 완료 - reservationId: {}, userId: {}", reservationId, userId)
    }
}
