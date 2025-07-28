package kr.hhplus.be.server.payment.event.handler

import kr.hhplus.be.server.auth.service.TokenService
import kr.hhplus.be.server.concert.event.SeatConfirmedEvent
import kr.hhplus.be.server.concert.service.SeatService
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.reservation.service.ReservationService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 관련 이벤트를 처리하는 핸들러
 * 
 * 이벤트 기반으로 처리하여:
 * 1. PaymentService와 다른 도메인 간의 결합도 감소
 * 2. 비동기 처리로 성능 향상
 * 3. 확장성 있는 아키텍처 구현
 */
@Component
class PaymentEventHandler(
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val tokenService: TokenService,
    private val eventPublisher: DomainEventPublisher
) {
    
    private val logger = LoggerFactory.getLogger(PaymentEventHandler::class.java)
    
    /**
     * 결제 완료 이벤트 처리
     * - 예약 확정
     * - 좌석 상태 업데이트
     * - 토큰 완료 처리
     */
    @Async
    @EventListener
    @Transactional
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        logger.info("결제 완료 이벤트 처리 시작: paymentId=${event.paymentId}")
        
        try {
            // 1. 예약 확정 (내부 메서드 호출로 락 없이 처리)
            reservationService.confirmReservationInternal(event.reservationId, event.paymentId)
            logger.info("예약 확정 완료: reservationId=${event.reservationId}")
            
            // 2. 좌석 상태 업데이트 (내부 메서드 호출로 락 없이 처리)
            val seatDto = seatService.confirmSeatInternal(event.seatId)
            logger.info("좌석 확정 완료: seatId=${event.seatId}")
            
            // 2-1. 좌석 확정 이벤트 발행
            val seatConfirmedEvent = SeatConfirmedEvent(
                seatId = event.seatId,
                scheduleId = seatDto.scheduleId,
                seatNumber = seatDto.seatNumber,
                userId = event.userId,
                reservationId = event.reservationId,
                paymentId = event.paymentId
            )
            eventPublisher.publish(seatConfirmedEvent)
            
            // 3. 토큰 완료 처리
            tokenService.completeReservation(event.token)
            logger.info("토큰 완료 처리: token=${event.token}")
            
            logger.info("결제 완료 이벤트 처리 완료: paymentId=${event.paymentId}")
            
        } catch (e: Exception) {
            logger.error("결제 완료 이벤트 처리 실패: paymentId=${event.paymentId}, error=${e.message}", e)
            // TODO: 보상 트랜잭션 또는 재시도 로직 추가 고려
        }
    }
    
    /**
     * 결제 실패 이벤트 처리
     * - 예약 취소 처리
     * - 알림 발송 등
     */
    @Async
    @EventListener
    @Transactional
    fun handlePaymentFailed(event: PaymentFailedEvent) {
        logger.info("결제 실패 이벤트 처리 시작: paymentId=${event.paymentId}, reason=${event.reason}")
        
        try {
            // 1. 토큰 완료 처리 (실패해도 토큰은 해제)
            tokenService.completeReservation(event.token)
            logger.info("토큰 해제 완료: token=${event.token}")
            
            // 2. 추가 실패 처리 로직 (알림 등)
            // TODO: 사용자에게 결제 실패 알림 발송
            
            logger.info("결제 실패 이벤트 처리 완료: paymentId=${event.paymentId}")
            
        } catch (e: Exception) {
            logger.error("결제 실패 이벤트 처리 중 오류: paymentId=${event.paymentId}, error=${e.message}", e)
        }
    }
}