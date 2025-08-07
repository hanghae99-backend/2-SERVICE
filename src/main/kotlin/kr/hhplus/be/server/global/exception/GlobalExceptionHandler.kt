package kr.hhplus.be.server.global.exception

import kr.hhplus.be.server.domain.auth.exception.InvalidTokenException
import kr.hhplus.be.server.domain.auth.exception.QueueFullException
import kr.hhplus.be.server.domain.auth.exception.TokenExpiredException
import kr.hhplus.be.server.domain.auth.exception.TokenIssuanceException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.balance.exception.InsufficientBalanceException
import kr.hhplus.be.server.domain.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.exception.ConcertScheduleNotFoundException
import kr.hhplus.be.server.domain.concert.exception.InvalidSeatStatusException
import kr.hhplus.be.server.domain.concert.exception.SeatAlreadyReservedException
import kr.hhplus.be.server.domain.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.domain.payment.exception.PaymentAlreadyProcessedException
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException
import kr.hhplus.be.server.domain.payment.exception.PaymentProcessException
import kr.hhplus.be.server.domain.reservation.exception.InvalidReservationStatusException
import kr.hhplus.be.server.domain.reservation.exception.ReservationAlreadyCancelledException
import kr.hhplus.be.server.domain.reservation.exception.ReservationExpiredException
import kr.hhplus.be.server.domain.reservation.exception.ReservationNotFoundException
import kr.hhplus.be.server.domain.user.exception.UserAlreadyExistsException
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.global.lock.ConcurrentAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

/**
 * 전역 예외 처리기
 * 모든 컨트롤러에서 발생하는 예외를 일관성 있게 처리
 */
@RestControllerAdvice
class GlobalExceptionHandler {


    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorMessage = ex.bindingResult.fieldErrors.joinToString(", ") { it.defaultMessage ?: "잘못된 값입니다" }
        return createErrorResponse(
            errorMessage,
            CommonErrorCode.BadRequest.code,
            CommonErrorCode.BadRequest.httpStatus
        )
    }


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


    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFoundException(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "사용자를 찾을 수 없습니다", ex.errorCode, ex.status)
    }

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExistsException(ex: UserAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex.message ?: "이미 존재하는 사용자입니다", ex.errorCode, ex.status)
    }

    // 분산락 실패: 동시 접근으로 인한 충돌
    @ExceptionHandler(ConcurrentAccessException::class)
    fun handleConcurrentAccessException(ex: ConcurrentAccessException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(
            ex.message ?: "동시 접근으로 인한 처리 실패입니다. 잠시 후 다시 시도해주세요.",
            "CONCURRENT_ACCESS_DENIED",
            HttpStatus.CONFLICT
        )
    }


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


    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        return createErrorResponse(
            ex.message ?: "런타임 오류가 발생했습니다",
            CommonErrorCode.RuntimeError.code,
            CommonErrorCode.RuntimeError.httpStatus
        )
    }


    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        return createErrorResponse(
            "서버 내부 오류가 발생했습니다",
            CommonErrorCode.InternalServerError.code,
            CommonErrorCode.InternalServerError.httpStatus
        )
    }

    // 비즈니스 상태 충돌: 이미 예약된 좌석 등
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        // 이미 예약된 좌석의 경우 409 Conflict 반환
        val status = if (ex.message?.contains("이미 예약된") == true) {
            HttpStatus.CONFLICT
        } else {
            HttpStatus.BAD_REQUEST
        }

        return createErrorResponse(
            ex.message ?: "잘못된 상태입니다",
            if (status == HttpStatus.CONFLICT) "ALREADY_RESERVED" else CommonErrorCode.BadRequest.code,
            status
        )
    }

    // 권한 및 매개변수 검증 예외
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        // 권한 관련 에러의 경우 403 Forbidden 반환
        val status = if (ex.message?.contains("소유자가 아닙니다") == true ||
            ex.message?.contains("권한이 없습니다") == true ||
            ex.message?.contains("예약 소유자가 아닙니다") == true) {
            HttpStatus.FORBIDDEN
        } else {
            HttpStatus.BAD_REQUEST
        }

        return createErrorResponse(
            ex.message ?: "잘못된 요청입니다",
            if (status == HttpStatus.FORBIDDEN) "ACCESS_DENIED" else CommonErrorCode.BadRequest.code,
            status
        )
    }


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
