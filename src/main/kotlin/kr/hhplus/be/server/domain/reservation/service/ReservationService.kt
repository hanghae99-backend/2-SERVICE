package kr.hhplus.be.server.domain.reservation.service

import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.kafka.ReservationEventProducer
import kr.hhplus.be.server.domain.reservation.exception.ReservationNotFoundException
import kr.hhplus.be.server.domain.reservation.exception.ReservationAccessDeniedException
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val statusRepository: ReservationStatusTypePojoRepository,
    private val reservationEventProducer: ReservationEventProducer,
    private val seatApiClient: kr.hhplus.be.server.global.client.SeatApiClient,
    private val activeTokenService: kr.hhplus.be.server.domain.auth.service.ActiveTokenService
) {
    
    private val logger = LoggerFactory.getLogger(ReservationService::class.java)
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun createReservation(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        // 토큰 검증
        if (!activeTokenService.isTokenActive(token)) {
            throw IllegalArgumentException("유효하지 않은 토큰입니다")
        }
        
        return createReservation(userId, concertId, seatId)
    }
    
    @LockGuard(
        key = "'seat:' + #seatId",
        strategy = LockStrategy.PUB_SUB,
        waitTimeoutMs = 2000L
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun createReservation(userId: Long, concertId: Long, seatId: Long): Reservation {
        seatApiClient.validateSeatAvailability(seatId)
        val seatInfo = seatApiClient.getSeatInfo(seatId)
        
        seatApiClient.reserveSeat(seatId)
        
        val reservation = try {
            createReservationWithSeatInfo(userId, concertId, seatId, seatInfo.seatNumber, seatInfo.price)
        } catch (e: Exception) {
            logger.error("예약 생성 실패 - seatId: $seatId", e)
            reservationEventProducer.sendReservationCancelledEvent(ReservationCancelledEvent(
                userId = userId,
                concertId = concertId,
                seatId = seatId,
                cancelReason = e.message ?: "Unknown error",
                isFailed = true
            ))
            throw e
        }

        reservationEventProducer.sendReservationCreatedEvent(ReservationCreatedEvent(
            reservationId = reservation.reservationId,
            userId = reservation.userId,
            concertId = reservation.concertId,
            seatId = reservation.seatId,
            seatNumber = reservation.seatNumber,
            price = reservation.price,
            expiresAt = reservation.expiresAt
        ))
        
        return reservation
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun confirmReservation(reservationId: Long, paymentId: Long): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
            
        return confirmReservation(reservation, paymentId)
    }
    
    private fun confirmReservation(reservation: Reservation, paymentId: Long): Reservation {
        reservation.confirm(paymentId, statusRepository.getConfirmedStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        return savedReservation
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun cancelReservationByUser(reservationId: Long, userId: Long, cancelReason: String?, token: String): Reservation {
        // 토큰 검증
        if (!activeTokenService.isTokenActive(token)) {
            throw IllegalArgumentException("유효하지 않은 토큰입니다")
        }
        
        return cancelReservationByUser(reservationId, userId, cancelReason)
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun cancelReservationByUser(reservationId: Long, userId: Long, cancelReason: String?): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
        
        if (reservation.userId != userId) {
            throw ReservationAccessDeniedException(userId, reservationId)
        }
        
        return cancelReservation(reservation, cancelReason ?: "사용자 취소", false)
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun cancelReservationBySystem(reservationId: Long, cancelReason: String): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
        
        return cancelReservation(reservation, cancelReason, true)
    }

    private fun cancelReservation(reservation: Reservation, cancelReason: String, isExpired: Boolean): Reservation {
        reservation.cancel(statusRepository.getCancelledStatus())
        val savedReservation = reservationRepository.save(reservation)
        
        reservationEventProducer.sendReservationCancelledEvent(ReservationCancelledEvent(
            reservationId = savedReservation.reservationId,
            userId = savedReservation.userId,
            concertId = savedReservation.concertId,
            seatId = savedReservation.seatId,
            cancelReason = cancelReason,
            isExpired = isExpired
        ))

        return savedReservation
    }
    
    fun getReservationById(reservationId: Long): Reservation {
        return reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException(reservationId)
    }


    fun getExpiredReservations(limit: Int = 100): List<ReservationDto> {
        val expiredReservations = reservationRepository.findByExpiresAtBeforeAndStatusCode(
            LocalDateTime.now(), 
            statusRepository.getTemporaryStatus().code
        ).take(limit)
        
        return expiredReservations.map { ReservationDto.fromEntity(it) }
    }

    
    private fun createReservationWithSeatInfo(
        userId: Long, 
        concertId: Long, 
        seatId: Long, 
        seatNumber: String, 
        price: BigDecimal
    ): Reservation {
        val reservation = Reservation.createTemporary(
            userId = userId,
            concertId = concertId,
            seatId = seatId,
            seatNumber = seatNumber,
            price = price,
            temporaryStatus = statusRepository.getTemporaryStatus()
        )
        
        return reservationRepository.save(reservation)
    }
    
    private fun createTemporaryReservation(userId: Long, concertId: Long, seatId: Long): Reservation {
        val reservation = Reservation.createTemporary(
            userId = userId,
            concertId = concertId,
            seatId = seatId,
            seatNumber = "TBD", // 이벤트로 좌석 정보 업데이트 예정
            price = BigDecimal.ZERO, // 이벤트로 가격 정보 업데이트 예정
            temporaryStatus = statusRepository.getTemporaryStatus()
        )
        
        return reservationRepository.save(reservation)
    }
    
    
}
