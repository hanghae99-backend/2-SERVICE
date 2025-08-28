package kr.hhplus.be.server.domain.payment.service

import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailureStage
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.repositories.PaymentRepository
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.global.lock.LockGuard
import kr.hhplus.be.server.global.lock.LockStrategy

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
    private val eventPublisher: DomainEventPublisher,
    private val balanceApiClient: kr.hhplus.be.server.global.client.BalanceApiClient,
    private val reservationApiClient: kr.hhplus.be.server.global.client.ReservationApiClient
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(PaymentService::class.java)
    }

    @LockGuard(
        key = "'payment:reservation:' + #reservationId",
        strategy = LockStrategy.SPIN,
        waitTimeoutMs = 5000L,
        retryIntervalMs = 100L,
        maxRetryCount = 30
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun processPayment(userId: Long, reservationId: Long, token: String, amount: BigDecimal): PaymentDto {
        logger.info("결제 프로세스 시작 - userId: {}, reservationId: {}, amount: {}", userId, reservationId, amount)
        
        try {
            val payment = createReservationPayment(userId, reservationId, amount)
            
            balanceApiClient.deductBalance(
                userId = userId,
                amount = amount,
                description = "콘서트 티켓 결제 - 예약ID: ${reservationId}"
            )
            
            reservationApiClient.confirmReservation(
                reservationId = reservationId,
                paymentId = payment.paymentId
            )
            
            // 토큰 완료 처리는 이벤트로 분리됨

            val completedStatus = paymentStatusTypeRepository.getCompletedStatus()
            val paymentEntity = paymentRepository.findById(payment.paymentId)
                ?: throw PaymentNotFoundException(payment.paymentId)
            
            paymentEntity.complete()
            paymentEntity.updateStatus(completedStatus)
            val completedPayment = paymentRepository.save(paymentEntity)
            
            eventPublisher.publish(PaymentCompletedEvent(
                paymentId = completedPayment.paymentId,
                userId = userId,
                reservationId = reservationId,
                amount = completedPayment.amount,
                token = token
            ))

            logger.info("결제 프로세스 완료 - userId: {}, paymentId: {}", userId, completedPayment.paymentId)
            return PaymentDto.fromEntity(completedPayment)
            
        } catch (e: Exception) {
            logger.error("결제 프로세스 실패 - userId: {}, reservationId: {}, error: {}", userId, reservationId, e.message, e)
            
            val failedPayment = createFailedPayment(userId, reservationId, amount, e.message ?: "알 수 없는 오류")
            
            eventPublisher.publish(PaymentFailedEvent(
                paymentId = failedPayment.paymentId,
                userId = userId,
                reservationId = reservationId,
                reason = e.message ?: "결제 처리 실패",
                token = token,
                amount = amount,
                needsBalanceRestore = true,
                failureStage = determineFailureStage(e)
            ))
            
            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

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

        if (payment.isCompleted()) {
            throw IllegalStateException("이미 완료된 결제입니다: $paymentId")
        }
        if (payment.isFailed()) {
            throw IllegalStateException("실패한 결제는 완료할 수 없습니다: $paymentId")
        }

        try {
            processActualPayment(payment)
            payment.complete()

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
        
        if (payment.paymentMethod != "POINT") {
            throw IllegalArgumentException("현재는 포인트 결제만 지원합니다: ${payment.paymentMethod}")
        }
        
        logger.debug("포인트 결제 완료 - paymentId: ${payment.paymentId}")
    }

    fun getPaymentById(paymentId: Long): PaymentDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException(paymentId) }

        return PaymentDto.fromEntity(payment)
    }
    
    private fun createFailedPayment(userId: Long, reservationId: Long, amount: BigDecimal, reason: String): PaymentDto {
        val failedStatus = paymentStatusTypeRepository.getFailedStatus()
        val payment = Payment.createForReservation(userId, reservationId, amount, "POINT", failedStatus)
        val savedPayment = paymentRepository.save(payment)
        return PaymentDto.fromEntity(savedPayment)
    }
    
    private fun determineFailureStage(exception: Exception): PaymentFailureStage {
        return when {
            exception.message?.contains("잔고") == true -> PaymentFailureStage.BALANCE_DEDUCTION
            exception.message?.contains("예약") == true -> PaymentFailureStage.RESERVATION_CONFIRM
            exception.message?.contains("토큰") == true -> PaymentFailureStage.TOKEN_COMPLETION
            else -> PaymentFailureStage.UNKNOWN
        }
    }

}
