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
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal


@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentStatusTypeRepository: PaymentStatusTypePojoRepository,
    private val domainEventPublisher: DomainEventPublisher
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(PaymentService::class.java)
    }

    // 예약 관련 결제
    @Transactional
    fun createReservationPayment(userId: Long, reservationId: Long, amount: BigDecimal): PaymentDto {
        val pendingStatus = paymentStatusTypeRepository.getPendingStatus()
        val payment = Payment.createForReservation(userId, reservationId, amount, "POINT", pendingStatus)
        val savedPayment = paymentRepository.save(payment)
        return PaymentDto.fromEntity(savedPayment)
    }
    
    // 일반 결제 (기존 방식 유지)
    @Transactional
    fun createPayment(userId: Long, amount: BigDecimal): PaymentDto {
        val pendingStatus = paymentStatusTypeRepository.getPendingStatus()
        val payment = Payment.create(userId, amount, "POINT", pendingStatus)
        val savedPayment = paymentRepository.save(payment)
        return PaymentDto.fromEntity(savedPayment)
    }

    @Transactional
    fun completePayment(paymentId: Long, reservationId: Long, seatId: Long, token: String): PaymentDto {
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

            val paymentCompletedEvent = PaymentCompletedEvent(
                paymentId = finalPayment.paymentId,
                userId = finalPayment.userId,
                reservationId = reservationId,
                seatId = seatId,
                amount = finalPayment.amount,
                token = token
            )
            domainEventPublisher.publish(paymentCompletedEvent)

            return PaymentDto.fromEntity(finalPayment)
            
        } catch (e: Exception) {
            logger.error("실제 결제 처리 실패 - paymentId: $paymentId, error: ${e.message}", e)
            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}", e)
        }
    }
    
    private fun processActualPayment(payment: Payment) {
        logger.info("실제 결제 처리 시작 - paymentId: ${payment.paymentId}, amount: ${payment.amount}")
        
        when (payment.paymentMethod) {
            "POINT" -> {
                // 포인트 결제는 이미 차감된 상태이므로 추가 처리 불필요
                logger.debug("포인트 결제 완료 - paymentId: ${payment.paymentId}")
            }
            "CARD" -> {
                // 실제 환경에서는 PG사 API 호출
                processCardPayment(payment)
            }
            "BANK" -> {
                // 실제 환경에서는 은행 API 호출  
                processBankTransfer(payment)
            }
            else -> {
                throw IllegalArgumentException("지원하지 않는 결제 방법입니다: ${payment.paymentMethod}")
            }
        }
    }
    
    private fun processCardPayment(payment: Payment) {
        // 실제 카드 결제 처리 시뮬레이션
        logger.info("카드 결제 처리 - paymentId: ${payment.paymentId}, amount: ${payment.amount}")
        
        // 실제 환경에서는 PG사 API 호출
        // val pgResult = pgService.processPayment(payment)
        // if (!pgResult.isSuccess) throw PaymentProcessException("카드 결제 실패")
        
        // 시뮬레이션을 위한 처리 시간
        Thread.sleep(100)
        
        logger.info("카드 결제 완료 - paymentId: ${payment.paymentId}")
    }
    
    private fun processBankTransfer(payment: Payment) {
        // 실제 계좌이체 처리 시뮬레이션
        logger.info("계좌이체 처리 - paymentId: ${payment.paymentId}, amount: ${payment.amount}")
        
        // 실제 환경에서는 은행 API 호출
        // val bankResult = bankService.transfer(payment)
        // if (!bankResult.isSuccess) throw PaymentProcessException("계좌이체 실패")
        
        // 시뮬레이션을 위한 처리 시간
        Thread.sleep(200)
        
        logger.info("계좌이체 완료 - paymentId: ${payment.paymentId}")
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

        val paymentFailedEvent = PaymentFailedEvent(
            paymentId = finalPayment.paymentId,
            userId = finalPayment.userId,
            reservationId = reservationId,
            reason = reason,
            token = token
        )
        domainEventPublisher.publish(paymentFailedEvent)

        return PaymentDto.fromEntity(finalPayment)
    }

    fun findByReservationId(reservationId: Long): List<Payment> {
        return paymentRepository.findByReservationId(reservationId)
    }
    
    fun getPaymentById(paymentId: Long): PaymentDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException(paymentId) }

        return PaymentDto.fromEntity(payment)
    }

    fun validatePaymentAmount(currentBalance: BigDecimal, paymentAmount: BigDecimal) {
        if (currentBalance < paymentAmount) {
            throw PaymentProcessException(
                "잔액이 부족합니다. 현재 잔액: $currentBalance, 필요 금액: $paymentAmount"
            )
        }
    }
}
