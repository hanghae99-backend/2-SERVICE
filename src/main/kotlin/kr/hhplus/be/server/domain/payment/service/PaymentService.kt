package kr.hhplus.be.server.domain.payment.service

import kr.hhplus.be.server.global.extension.orElseThrow
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.api.payment.dto.PaymentDto
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import kr.hhplus.be.server.domain.payment.repository.PaymentStatusTypePojoRepository

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
            .orElseThrow { PaymentNotFoundException("결제를 찾을 수 없습니다: $paymentId") }


        val completedStatus = paymentStatusTypeRepository.getCompletedStatus()
        val completedPayment = payment.complete(completedStatus)
        val finalPayment = paymentRepository.save(completedPayment)


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
    }

    @Transactional
    fun failPayment(paymentId: Long, reservationId: Long, reason: String, token: String): PaymentDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException("결제를 찾을 수 없습니다: $paymentId") }


        val failedStatus = paymentStatusTypeRepository.getFailedStatus()
        val failedPayment = payment.fail(failedStatus)
        val finalPayment = paymentRepository.save(failedPayment)


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

    fun getPaymentById(paymentId: Long): PaymentDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException("결제를 찾을 수 없습니다: $paymentId") }

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
