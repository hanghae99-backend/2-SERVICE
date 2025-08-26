package kr.hhplus.be.server.domain.payment.service

import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.repositories.PaymentRepository
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal


@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentStatusTypeRepository: PaymentStatusTypePojoRepository,
    private val eventPublisher: DomainEventPublisher
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(PaymentService::class.java)
    }

    /**
     * 결제 요청을 생성하고 이벤트를 발행합니다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun processPayment(userId: Long, reservationId: Long, token: String, amount: BigDecimal): PaymentDto {
        logger.info("결제 프로세스 시작 - userId: {}, reservationId: {}, amount: {}", userId, reservationId, amount)
        
        val payment = createReservationPayment(userId, reservationId, amount)
        
        // 결제 요청 이벤트 발행 - 이벤트 핸들러에서 잔고 차감과 예약 확정을 HTTP API로 처리
        eventPublisher.publish(PaymentCompletedEvent(
            paymentId = payment.paymentId,
            userId = userId,
            reservationId = reservationId,
            amount = payment.amount,
            token = token
        ))

        logger.info("결제 프로세스 시작 완료, 이벤트 발행 - userId: {}, paymentId: {}", userId, payment.paymentId)
        return payment
    }

    // 예약 관련 결제
    @Transactional
    fun createReservationPayment(userId: Long, reservationId: Long, amount: BigDecimal): PaymentDto {
        val pendingStatus = paymentStatusTypeRepository.getPendingStatus()
        val payment = Payment.createForReservation(userId, reservationId, amount, "POINT", pendingStatus)
        val savedPayment = paymentRepository.save(payment)
        return PaymentDto.fromEntity(savedPayment)
    }
    

    @Transactional
    fun completePayment(paymentId: Long, reservationId: Long, seatId: Long, token: String, scheduleId: Long, seatNumber: String, concertId: Long): PaymentDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException(paymentId) }

        // 결제 상태 검증
        if (payment.isCompleted()) {
            throw IllegalStateException("이미 완료된 결제입니다: $paymentId")
        }
        
        if (payment.isFailed()) {
            throw IllegalStateException("실패한 결제는 완료할 수 없습니다: $paymentId")
        }

        // 실제 결제 처리 로직
        try {
            processActualPayment(payment)
            
            // 비즈니스 로직 검증 및 완료 처리
            payment.complete()
            
            // 상태 업데이트
            val completedStatus = paymentStatusTypeRepository.getCompletedStatus()
            payment.updateStatus(completedStatus)
            
            val finalPayment = paymentRepository.save(payment)

            return PaymentDto.fromEntity(finalPayment)
            
        } catch (e: Exception) {
            logger.error("실제 결제 처리 실패 - paymentId: $paymentId, error: ${e.message}", e)
            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}", e)
        }
    }
    
    private fun processActualPayment(payment: Payment) {
        logger.info("실제 결제 처리 시작 - paymentId: ${payment.paymentId}, amount: ${payment.amount}")
        
        // 현재는 포인트 결제만 지원
        if (payment.paymentMethod != "POINT") {
            throw IllegalArgumentException("현재는 포인트 결제만 지원합니다: ${payment.paymentMethod}")
        }
        
        logger.debug("포인트 결제 완료 - paymentId: ${payment.paymentId}")
    }

    @Transactional
    fun failPayment(paymentId: Long, reservationId: Long, reason: String, token: String): PaymentDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException(paymentId) }

        // 비즈니스 로직 검증
        payment.fail()
        
        // 상태 업데이트
        val failedStatus = paymentStatusTypeRepository.getFailedStatus()
        payment.updateStatus(failedStatus)
        
        val finalPayment = paymentRepository.save(payment)

        return PaymentDto.fromEntity(finalPayment)
    }
    
    fun getPaymentById(paymentId: Long): PaymentDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException(paymentId) }

        return PaymentDto.fromEntity(payment)
    }

}
