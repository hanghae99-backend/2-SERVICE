package kr.hhplus.be.server.domain.common

import kr.hhplus.be.server.global.event.AbstractDomainEvent
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 개선된 도메인 이벤트들 - 이벤트 순서와 처리 보장을 위한 개선
 */

/**
 * 예약 관련 이벤트들
 */
sealed class ReservationDomainEvent : AbstractDomainEvent() {
    abstract val reservationId: Long
    abstract val userId: Long
    abstract val aggregateVersion: Long  // 이벤트 순서 보장을 위한 버전
}

data class ReservationCreatedEvent(
    override val reservationId: Long,
    override val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val expiresAt: LocalDateTime?,
    override val aggregateVersion: Long = 1L
) : ReservationDomainEvent() {
    override val eventType: String = "ReservationCreated"
    
    // 비즈니스 맥락 정보 추가
    val reservationContext = ReservationContext(
        concertId = concertId,
        seatId = seatId,
        seatNumber = seatNumber,
        price = price,
        timeoutMinutes = if (expiresAt != null) {
            java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes()
        } else null
    )
}

data class ReservationConfirmedEvent(
    override val reservationId: Long,
    override val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val paymentId: Long,
    val price: BigDecimal,
    override val aggregateVersion: Long = 2L
) : ReservationDomainEvent() {
    override val eventType: String = "ReservationConfirmed"
}

data class ReservationCancelledEvent(
    override val reservationId: Long,
    override val userId: Long,
    val concertId: Long,
    val seatId: Long,
    val cancelReason: String,
    val isExpired: Boolean = false,
    val refundRequired: Boolean = false,
    override val aggregateVersion: Long
) : ReservationDomainEvent() {
    override val eventType: String = "ReservationCancelled"
}

/**
 * 결제 관련 이벤트들
 */
sealed class PaymentDomainEvent : AbstractDomainEvent() {
    abstract val paymentId: Long
    abstract val userId: Long
}

data class PaymentInitiatedEvent(
    override val paymentId: Long,
    override val userId: Long,
    val reservationId: Long,
    val amount: BigDecimal,
    val paymentMethod: String
) : PaymentDomainEvent() {
    override val eventType: String = "PaymentInitiated"
}

data class PaymentCompletedEvent(
    override val paymentId: Long,
    override val userId: Long,
    val reservationId: Long,
    val seatId: Long,
    val amount: BigDecimal,
    val token: String
) : PaymentDomainEvent() {
    override val eventType: String = "PaymentCompleted"
}

data class PaymentFailedEvent(
    override val paymentId: Long,
    override val userId: Long,
    val reservationId: Long,
    val reason: String,
    val token: String,
    val retryCount: Int = 0,
    val isRetryable: Boolean = false
) : PaymentDomainEvent() {
    override val eventType: String = "PaymentFailed"
}

/**
 * 잔액 관련 이벤트들
 */
sealed class BalanceDomainEvent : AbstractDomainEvent() {
    abstract val userId: Long
    abstract val amount: BigDecimal
}

data class BalanceChargedEvent(
    override val userId: Long,
    override val amount: BigDecimal,
    val newBalance: BigDecimal,
    val chargeMethod: String = "MANUAL"
) : BalanceDomainEvent() {
    override val eventType: String = "BalanceCharged"
}

data class BalanceDeductedEvent(
    override val userId: Long,
    override val amount: BigDecimal,
    val newBalance: BigDecimal,
    val deductionReason: String,
    val relatedEntityId: Long? = null
) : BalanceDomainEvent() {
    override val eventType: String = "BalanceDeducted"
}

/**
 * 좌석 관련 이벤트들
 */
sealed class SeatDomainEvent : AbstractDomainEvent() {
    abstract val seatId: Long
    abstract val scheduleId: Long
}

data class SeatReservedEvent(
    override val seatId: Long,
    override val scheduleId: Long,
    val userId: Long,
    val reservationId: Long,
    val seatNumber: String
) : SeatDomainEvent() {
    override val eventType: String = "SeatReserved"
}

data class SeatReleasedEvent(
    override val seatId: Long,
    override val scheduleId: Long,
    val userId: Long,
    val reservationId: Long,
    val releaseReason: String
) : SeatDomainEvent() {
    override val eventType: String = "SeatReleased"
}

data class SeatConfirmedEvent(
    override val seatId: Long,
    override val scheduleId: Long,
    val userId: Long,
    val reservationId: Long,
    val paymentId: Long
) : SeatDomainEvent() {
    override val eventType: String = "SeatConfirmed"
}

/**
 * 토큰 관련 이벤트들
 */
sealed class TokenDomainEvent : AbstractDomainEvent() {
    abstract val token: String
    abstract val userId: Long
}

data class TokenIssuedEvent(
    override val token: String,
    override val userId: Long,
    val queuePosition: Int,
    val estimatedWaitTime: Long
) : TokenDomainEvent() {
    override val eventType: String = "TokenIssued"
}

data class TokenActivatedEvent(
    override val token: String,
    override val userId: Long,
    val activatedAt: LocalDateTime = LocalDateTime.now()
) : TokenDomainEvent() {
    override val eventType: String = "TokenActivated"
}

data class TokenExpiredEvent(
    override val token: String,
    override val userId: Long,
    val expiredAt: LocalDateTime = LocalDateTime.now(),
    val reason: String = "TIMEOUT"
) : TokenDomainEvent() {
    override val eventType: String = "TokenExpired"
}

/**
 * 이벤트 처리를 위한 컨텍스트 정보
 */
data class ReservationContext(
    val concertId: Long,
    val seatId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val timeoutMinutes: Long?
)

data class PaymentContext(
    val paymentMethod: String,
    val currency: String = "KRW",
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 이벤트 처리 결과를 추적하기 위한 이벤트
 */
data class EventProcessingCompletedEvent(
    val originalEventType: String,
    val originalEventId: String,
    val processingResult: EventProcessingResult,
    val processedAt: LocalDateTime = LocalDateTime.now()
) : AbstractDomainEvent() {
    override val eventType: String = "EventProcessingCompleted"
}

data class EventProcessingFailedEvent(
    val originalEventType: String,
    val originalEventId: String,
    val failureReason: String,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: LocalDateTime?,
    val failedAt: LocalDateTime = LocalDateTime.now()
) : AbstractDomainEvent() {
    override val eventType: String = "EventProcessingFailed"
}

enum class EventProcessingResult {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    SKIPPED
}
