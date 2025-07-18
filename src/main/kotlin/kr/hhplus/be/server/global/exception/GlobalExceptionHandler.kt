package kr.hhplus.be.server.global.exception

import kr.hhplus.be.server.auth.entity.*
import kr.hhplus.be.server.balance.entity.*
import kr.hhplus.be.server.concert.entity.*
import kr.hhplus.be.server.payment.entity.*
import kr.hhplus.be.server.user.entity.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

/**
 * 전역 예외 처리기
 * 모든 컨트롤러에서 발생하는 예외를 일관성 있게 처리
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * 파라미터 검증 실패 예외 처리
     */
    @ExceptionHandler(ParameterValidationException::class)
    fun handleParameterValidationException(ex: ParameterValidationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Parameter Validation Error",
            message = ex.message ?: "파라미터 검증에 실패했습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * 도메인 검증 실패 예외 처리
     */
    @ExceptionHandler(DomainValidationException::class)
    fun handleDomainValidationException(ex: DomainValidationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Domain Validation Error",
            message = ex.message ?: "도메인 검증에 실패했습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * 리소스를 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFoundException(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.NOT_FOUND.value(),
            error = "Resource Not Found",
            message = ex.message ?: "요청한 리소스를 찾을 수 없습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    /**
     * 비즈니스 규칙 위반 예외 처리
     */
    @ExceptionHandler(BusinessRuleViolationException::class)
    fun handleBusinessRuleViolationException(ex: BusinessRuleViolationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.CONFLICT.value(),
            error = "Business Rule Violation",
            message = ex.message ?: "비즈니스 규칙을 위반했습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    /**
     * 특정 도메인 예외들 - 더 구체적인 HTTP 상태 코드가 필요한 경우
     */
    @ExceptionHandler(TokenActivationException::class)
    fun handleTokenActivationException(ex: TokenActivationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.FORBIDDEN.value(),
            error = "Token Not Active",
            message = ex.message ?: "활성화된 토큰이 아닙니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
    }



    @ExceptionHandler(ReservationExpiredException::class)
    fun handleReservationExpiredException(ex: ReservationExpiredException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.GONE.value(),
            error = "Reservation Expired",
            message = ex.message ?: "예약이 만료되었습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.GONE).body(errorResponse)
    }

    @ExceptionHandler(InvalidReservationStatusException::class)
    fun handleInvalidReservationStatusException(ex: InvalidReservationStatusException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Invalid Reservation Status",
            message = ex.message ?: "올바르지 않은 예약 상태입니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * 결제 관련 예외 처리
     */
    @ExceptionHandler(PaymentProcessException::class)
    fun handlePaymentProcessException(ex: PaymentProcessException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Payment Process Error",
            message = ex.message ?: "결제 처리 중 오류가 발생했습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(PaymentAlreadyProcessedException::class)
    fun handlePaymentAlreadyProcessedException(ex: PaymentAlreadyProcessedException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.CONFLICT.value(),
            error = "Payment Already Processed",
            message = ex.message ?: "이미 처리된 결제입니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    /**
     * 잔액 관련 예외 처리
     */
    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalanceException(ex: InsufficientBalanceException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Insufficient Balance",
            message = ex.message ?: "잔액이 부족합니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(PointNotFoundException::class)
    fun handlePointNotFoundException(ex: PointNotFoundException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.NOT_FOUND.value(),
            error = "Point Not Found",
            message = ex.message ?: "포인트 정보를 찾을 수 없습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    /**
     * 일반적인 RuntimeException 처리
     */
    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Runtime Error",
            message = ex.message ?: "런타임 오류가 발생했습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "서버 내부 오류가 발생했습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    /**
     * 도메인 예외 기본 처리 (위의 구체적인 핸들러에서 처리되지 않은 경우)
     */
    @ExceptionHandler(DomainException::class)
    fun handleDomainException(ex: DomainException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Domain Error",
            message = ex.message ?: "도메인 오류가 발생했습니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * 레거시 IllegalArgumentException 처리 (점진적 마이그레이션을 위해 유지)
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.message ?: "잘못된 요청입니다",
            path = ""
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }
}
