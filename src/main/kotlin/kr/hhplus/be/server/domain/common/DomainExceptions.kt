package kr.hhplus.be.server.domain.common

import kr.hhplus.be.server.global.exception.DomainException
import kr.hhplus.be.server.global.exception.ApplicationException

/**
 * 계층별 예외 분리 및 개선
 */

// ===== 도메인 예외 =====

/**
 * 예약 도메인 예외
 */
sealed class ReservationDomainException(message: String, cause: Throwable? = null) : DomainException("RESERVATION", message, "DOMAIN_ERROR", cause = cause)

class ReservationExpiredException(reservationId: Long) : 
    ReservationDomainException("예약이 만료되었습니다: $reservationId")

class InvalidReservationStateException(current: String, requested: String) :
    ReservationDomainException("잘못된 상태 변경 요청: $current -> $requested")

class ReservationTimeoutException(reservationId: Long, timeoutMinutes: Long) :
    ReservationDomainException("예약 시간이 초과되었습니다: $reservationId (${timeoutMinutes}분)")

class ReservationLimitExceededException(userId: Long, current: Int, limit: Int) :
    ReservationDomainException("예약 한도 초과: 사용자 $userId, 현재 $current, 한도 $limit")

/**
 * 결제 도메인 예외
 */
sealed class PaymentDomainException(message: String, cause: Throwable? = null) : DomainException("PAYMENT", message, "DOMAIN_ERROR", cause = cause)

class InvalidPaymentAmountException(amount: String) :
    PaymentDomainException("유효하지 않은 결제 금액: $amount")

class PaymentAlreadyProcessedException(paymentId: Long, currentStatus: String) :
    PaymentDomainException("이미 처리된 결제: $paymentId, 현재 상태: $currentStatus")

class InsufficientBalanceException(required: String, available: String) :
    PaymentDomainException("잔액 부족: 필요 $required, 보유 $available")

class DailyPaymentLimitExceededException(userId: Long, todayTotal: String, requested: String, limit: String) :
    PaymentDomainException("일일 결제 한도 초과: 사용자 $userId, 오늘 총액 $todayTotal, 요청 $requested, 한도 $limit")

/**
 * 좌석 도메인 예외
 */
sealed class SeatDomainException(message: String, cause: Throwable? = null) : DomainException("SEAT", message, "DOMAIN_ERROR", cause = cause)

class SeatNotAvailableException(seatId: Long, currentStatus: String) :
    SeatDomainException("예약 불가능한 좌석: $seatId, 현재 상태: $currentStatus")

class InvalidSeatStateTransitionException(seatId: Long, from: String, to: String) :
    SeatDomainException("유효하지 않은 좌석 상태 변경: $seatId, $from -> $to")

class SeatMaintenanceException(seatId: Long, reason: String) :
    SeatDomainException("좌석 정비 중: $seatId, 사유: $reason")

/**
 * 잔액 도메인 예외
 */
sealed class BalanceDomainException(message: String, cause: Throwable? = null) : DomainException("BALANCE", message, "DOMAIN_ERROR", cause = cause)

class InvalidChargeAmountException(amount: String, min: String, max: String) :
    BalanceDomainException("유효하지 않은 충전 금액: $amount (범위: $min ~ $max)")

class BalanceLimitExceededException(current: String, requested: String, limit: String) :
    BalanceDomainException("잔액 한도 초과: 현재 $current, 요청 $requested, 한도 $limit")

class DailyChargeLimitExceededException(userId: Long, todayTotal: String, requested: String, limit: String) :
    BalanceDomainException("일일 충전 한도 초과: 사용자 $userId, 오늘 총액 $todayTotal, 요청 $requested, 한도 $limit")

/**
 * 토큰 도메인 예외
 */
sealed class TokenDomainException(message: String, cause: Throwable? = null) : DomainException("TOKEN", message, "DOMAIN_ERROR", cause = cause)

class TokenExpiredException(token: String) :
    TokenDomainException("만료된 토큰: $token")

class InvalidTokenStateException(token: String, currentState: String, requiredState: String) :
    TokenDomainException("유효하지 않은 토큰 상태: $token, 현재: $currentState, 필요: $requiredState")

class TokenNotFoundException(token: String) :
    TokenDomainException("토큰을 찾을 수 없음: $token")

// ===== 애플리케이션 예외 =====

/**
 * 동시성 관련 예외
 */
sealed class ConcurrencyException(message: String, cause: Throwable? = null) : ApplicationException(message, cause)

class ConcurrentReservationException(seatId: Long) :
    ConcurrencyException("동시 예약으로 인한 충돌: 좌석 $seatId")

class OptimisticLockException(entityType: String, entityId: Long, version: Long) :
    ConcurrencyException("낙관적 락 충돌: $entityType $entityId, 버전: $version")

class DistributedLockTimeoutException(lockKey: String, timeoutMs: Long) :
    ConcurrencyException("분산 락 타임아웃: $lockKey, ${timeoutMs}ms")

/**
 * 외부 시스템 연동 예외
 */
sealed class ExternalSystemException(message: String, cause: Throwable? = null) : ApplicationException(message, cause)

class PaymentGatewayException(gateway: String, errorCode: String, errorMessage: String) :
    ExternalSystemException("결제 게이트웨이 오류: $gateway, 코드: $errorCode, 메시지: $errorMessage")

class NotificationServiceException(service: String, reason: String) :
    ExternalSystemException("알림 서비스 오류: $service, 사유: $reason")

class CacheServiceException(operation: String, key: String, cause: Throwable? = null) :
    ExternalSystemException("캐시 서비스 오류: $operation, 키: $key", cause)

/**
 * 비즈니스 규칙 위반 예외
 */
class BusinessRuleViolationException(
    val ruleCode: String,
    val ruleDescription: String,
    val violationDetails: Map<String, Any> = emptyMap()
) : ApplicationException("비즈니스 규칙 위반: $ruleDescription") {
    
    constructor(message: String) : this("GENERAL", message)
    
    fun getViolationDetail(key: String): Any? = violationDetails[key]
    
    fun withDetail(key: String, value: Any): BusinessRuleViolationException {
        return BusinessRuleViolationException(
            ruleCode = this.ruleCode,
            ruleDescription = this.message ?: "Unknown rule violation",
            violationDetails = this.violationDetails + (key to value)
        )
    }
}

/**
 * 리소스 관련 예외
 */
sealed class ResourceException(message: String, cause: Throwable? = null) : ApplicationException(message, cause)

class ResourceNotFoundException(resourceType: String, resourceId: String) :
    ResourceException("리소스를 찾을 수 없음: $resourceType $resourceId")

class ResourceAlreadyExistsException(resourceType: String, resourceId: String) :
    ResourceException("리소스가 이미 존재함: $resourceType $resourceId")

class ResourceAccessDeniedException(resourceType: String, resourceId: String, userId: Long) :
    ResourceException("리소스 접근 권한 없음: $resourceType $resourceId, 사용자: $userId")

/**
 * 예외 팩토리 - 일관된 예외 생성을 위한 유틸리티
 */
object DomainExceptionFactory {
    
    fun reservationExpired(reservationId: Long): ReservationExpiredException {
        return ReservationExpiredException(reservationId)
    }
    
    fun invalidReservationState(reservationId: Long, from: String, to: String): InvalidReservationStateException {
        return InvalidReservationStateException(from, to)
    }
    
    fun insufficientBalance(userId: Long, required: String, available: String): InsufficientBalanceException {
        return InsufficientBalanceException(required, available)
    }
    
    fun seatNotAvailable(seatId: Long, currentStatus: String): SeatNotAvailableException {
        return SeatNotAvailableException(seatId, currentStatus)
    }
    
    fun tokenExpired(token: String): TokenExpiredException {
        return TokenExpiredException(token)
    }
    
    fun concurrentReservation(seatId: Long): ConcurrentReservationException {
        return ConcurrentReservationException(seatId)
    }
    
    fun businessRuleViolation(ruleCode: String, description: String): BusinessRuleViolationException {
        return BusinessRuleViolationException(ruleCode, description)
    }
}
