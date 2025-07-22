package kr.hhplus.be.server.global.exception

import kr.hhplus.be.server.auth.exception.*
import kr.hhplus.be.server.balance.exception.*
import kr.hhplus.be.server.concert.exception.*
import kr.hhplus.be.server.payment.exception.*
import kr.hhplus.be.server.user.exception.*
import kr.hhplus.be.server.reservation.exception.*
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
     * Concert Domain Exceptions
     */
    @ExceptionHandler(
        ConcertNotFoundException::class,
        SeatNotFoundException::class,
        ConcertScheduleNotFoundException::class
    )
    fun handleNotFoundExceptions(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val (errorCode, status) = when (ex) {
            is ConcertNotFoundException -> ex.errorCode to ex.status
            is SeatNotFoundException -> ex.errorCode to ex.status
            is ConcertScheduleNotFoundException -> ex.errorCode to ex.status
            else -> "RESOURCE_NOT_FOUND" to HttpStatus.NOT_FOUND
        }
        
        return createErrorResponse(ex.message ?: "리소스를 찾을 수 없습니다", errorCode, status)
    }

    @ExceptionHandler(
        InvalidSeatStatusException::class,
        SeatAlreadyReservedException::class
    )
    fun handleConcertBusinessExceptions(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val (errorCode, status) = when (ex) {
            is InvalidSeatStatusException -> ex.errorCode to ex.status
            is SeatAlreadyReservedException -> ex.errorCode to ex.status
            else -> CommonErrorCode.BadRequest.code to CommonErrorCode.BadRequest.httpStatus
        }
        
        return createErrorResponse(ex.message ?: "비즈니스 규칙을 위반했습니다", errorCode, status)
    }

    /**
     * Balance Domain Exceptions
     */
    @ExceptionHandler(
        InsufficientBalanceException::class,
        InvalidPointAmountException::class
    )
    fun handleBalanceExceptions(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val (errorCode, status) = when (ex) {
            is InsufficientBalanceException -> ex.errorCode to ex.status
            is InvalidPointAmountException -> ex.errorCode to ex.status
            else -> CommonErrorCode.BadRequest.code to CommonErrorCode.BadRequest.httpStatus
        }
        
        return createErrorResponse(ex.message ?: "잔액 관련 오류가 발생했습니다", errorCode, status)
    }

    @ExceptionHandler(PointNotFoundException::class)
    fun handlePointNotFoundException(ex: PointNotFoundException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "포인트 정보를 찾을 수 없습니다", ex.errorCode, ex.status)
    }

    /**
     * Payment Domain Exceptions
     */
    @ExceptionHandler(PaymentNotFoundException::class)
    fun handlePaymentNotFoundException(ex: PaymentNotFoundException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "결제 정보를 찾을 수 없습니다", ex.errorCode, ex.status)
    }

    @ExceptionHandler(
        PaymentAlreadyProcessedException::class,
        PaymentProcessException::class
    )
    fun handlePaymentExceptions(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val (errorCode, status) = when (ex) {
            is PaymentAlreadyProcessedException -> ex.errorCode to ex.status
            is PaymentProcessException -> ex.errorCode to ex.status
            else -> CommonErrorCode.BadRequest.code to CommonErrorCode.BadRequest.httpStatus
        }
        
        return createErrorResponse(ex.message ?: "결제 관련 오류가 발생했습니다", errorCode, status)
    }

    /**
     * Reservation Domain Exceptions
     */
    @ExceptionHandler(ReservationNotFoundException::class)
    fun handleReservationNotFoundException(ex: ReservationNotFoundException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "예약 정보를 찾을 수 없습니다", ex.errorCode, ex.status)
    }

    @ExceptionHandler(ReservationExpiredException::class)
    fun handleReservationExpiredException(ex: ReservationExpiredException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "예약이 만료되었습니다", ex.errorCode, ex.status)
    }

    @ExceptionHandler(
        InvalidReservationStatusException::class,
        ReservationAlreadyCancelledException::class
    )
    fun handleReservationBusinessExceptions(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val (errorCode, status) = when (ex) {
            is InvalidReservationStatusException -> ex.errorCode to ex.status
            is ReservationAlreadyCancelledException -> ex.errorCode to ex.status
            else -> CommonErrorCode.BadRequest.code to CommonErrorCode.BadRequest.httpStatus
        }
        
        return createErrorResponse(ex.message ?: "예약 관련 오류가 발생했습니다", errorCode, status)
    }

    /**
     * User Domain Exceptions
     */
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFoundException(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "사용자를 찾을 수 없습니다", ex.errorCode, ex.status)
    }

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExistsException(ex: UserAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "이미 존재하는 사용자입니다", ex.errorCode, ex.status)
    }

    /**
     * Auth Domain Exceptions
     */
    @ExceptionHandler(
        TokenNotFoundException::class,
        TokenExpiredException::class,
        InvalidTokenException::class
    )
    fun handleAuthExceptions(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val (errorCode, status) = when (ex) {
            is TokenNotFoundException -> ex.errorCode to ex.status
            is TokenExpiredException -> ex.errorCode to ex.status
            is InvalidTokenException -> ex.errorCode to ex.status
            else -> "AUTH_ERROR" to HttpStatus.UNAUTHORIZED
        }
        
        return createErrorResponse(ex.message ?: "인증 관련 오류가 발생했습니다", errorCode, status)
    }

    @ExceptionHandler(TokenIssuanceException::class)
    fun handleTokenIssuanceException(ex: TokenIssuanceException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "토큰 발급에 실패했습니다", ex.errorCode, ex.status)
    }

    @ExceptionHandler(QueueFullException::class)
    fun handleQueueFullException(ex: QueueFullException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "대기열이 가득 찼습니다", ex.errorCode, ex.status)
    }

    /**
     * 일반적인 RuntimeException 처리
     */
    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(
            ex.message ?: "런타임 오류가 발생했습니다",
            CommonErrorCode.RuntimeError.code,
            CommonErrorCode.RuntimeError.httpStatus
        )
    }

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        return createErrorResponse(
            "서버 내부 오류가 발생했습니다",
            CommonErrorCode.InternalServerError.code,
            CommonErrorCode.InternalServerError.httpStatus
        )
    }

    /**
     * 레거시 IllegalArgumentException 처리 (점진적 마이그레이션을 위해 유지)
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(
            ex.message ?: "잘못된 요청입니다",
            CommonErrorCode.BadRequest.code,
            CommonErrorCode.BadRequest.httpStatus
        )
    }

    /**
     * 공통 ErrorResponse 생성 메서드
     */
    private fun createErrorResponse(
        message: String,
        errorCode: String,
        status: HttpStatus
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            error = errorCode,
            message = message,
            path = "" // 필요시 HttpServletRequest에서 path 추출 가능
        )
        return ResponseEntity.status(status).body(errorResponse)
    }
}
