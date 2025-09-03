package kr.hhplus.be.server.global.exception

import kr.hhplus.be.server.global.lock.ConcurrentAccessException
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.data.redis.RedisConnectionFailureException
import java.time.LocalDateTime
import java.util.concurrent.TimeoutException

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
        
        // 에러 코드 체계
        private const val BUSINESS_PREFIX = "BIZ"
        private const val VALIDATION_PREFIX = "VAL"
        private const val SYSTEM_PREFIX = "SYS"
        private const val INFRASTRUCTURE_PREFIX = "INF"
        private const val SECURITY_PREFIX = "SEC"
    }

    // === 비즈니스 예외 처리 ===
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 비즈니스 예외: {}", requestInfo, e.message, e)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "비즈니스 로직 오류가 발생했습니다",
            errorCode = e.getFullErrorCode()
        )
        
        return ResponseEntity.status(e.httpStatus).body(errorResponse)
    }

    // === 동시성 및 락 관련 예외 ===
    @ExceptionHandler(ConcurrentAccessException::class)
    fun handleConcurrentAccessException(e: ConcurrentAccessException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 동시 접근 예외: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "요청이 집중되어 처리할 수 없습니다. 잠시 후 다시 시도해주세요",
            errorCode = "$SYSTEM_PREFIX.CONCURRENT_ACCESS"
        )
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse)
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailureException(e: OptimisticLockingFailureException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 낙관적 락 충돌: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "데이터가 다른 사용자에 의해 수정되었습니다. 새로고침 후 다시 시도해주세요",
            errorCode = "$SYSTEM_PREFIX.OPTIMISTIC_LOCK_FAILURE"
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    @ExceptionHandler(TimeoutException::class)
    fun handleTimeoutException(e: TimeoutException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 타임아웃 예외: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "처리 시간이 초과되었습니다. 잠시 후 다시 시도해주세요",
            errorCode = "$SYSTEM_PREFIX.TIMEOUT"
        )
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse)
    }

    // === 데이터 검증 관련 예외 ===
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        val fieldErrors = e.bindingResult.fieldErrors
        
        logger.warn("[{}] 유효성 검사 실패: {}", requestInfo, 
            fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" })
        
        val errorMessage = fieldErrors.firstOrNull()?.defaultMessage 
            ?: "유효하지 않은 요청 데이터입니다"
        
        val errorDetails = fieldErrors.associate { it.field to (it.defaultMessage ?: "유효하지 않은 값") }
        logger.debug("검증 오류 상세: {}", errorDetails)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = errorMessage,
            errorCode = "$VALIDATION_PREFIX.INVALID_INPUT"
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(e: ConstraintViolationException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 제약 조건 위반: {}", requestInfo, e.message)
        
        val errorMessage = e.constraintViolations.firstOrNull()?.message 
            ?: "제약 조건 위반"
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = errorMessage,
            errorCode = "$VALIDATION_PREFIX.CONSTRAINT_VIOLATION"
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 타입 불일치: {} = {} (expected: {})", 
            requestInfo, e.name, e.value, e.requiredType?.simpleName)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "올바른 형식의 값을 입력해주세요: ${e.name}",
            errorCode = "$VALIDATION_PREFIX.TYPE_MISMATCH"
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    // === 데이터베이스 관련 예외 ===
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(e: DataIntegrityViolationException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.error("[{}] 데이터 무결성 위반: {}", requestInfo, e.message, e)
        
        val message = when {
            e.message?.contains("unique", ignoreCase = true) == true -> "이미 존재하는 데이터입니다"
            e.message?.contains("foreign key", ignoreCase = true) == true -> "참조 무결성 위반입니다"
            e.message?.contains("not null", ignoreCase = true) == true -> "필수 값이 누락되었습니다"
            else -> "데이터 처리 중 오류가 발생했습니다"
        }
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = message,
            errorCode = "$INFRASTRUCTURE_PREFIX.DATA_INTEGRITY_VIOLATION"
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    // === 인프라스트럭처 관련 예외 ===
    @ExceptionHandler(RedisConnectionFailureException::class)
    fun handleRedisConnectionFailureException(e: RedisConnectionFailureException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.error("[{}] Redis 연결 실패: {}", requestInfo, e.message, e)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "캐시 서비스 연결에 실패했습니다. 잠시 후 다시 시도해주세요",
            errorCode = "$INFRASTRUCTURE_PREFIX.REDIS_CONNECTION_FAILURE"
        )
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse)
    }

    // === HTTP 관련 예외 ===
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupportedException(e: HttpRequestMethodNotSupportedException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 지원하지 않는 HTTP 메서드: {}", requestInfo, e.method)
        
        val supportedMethods = e.supportedHttpMethods?.joinToString(", ") ?: "알 수 없음"
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "지원하지 않는 HTTP 메서드입니다. 지원되는 메서드: $supportedMethods",
            errorCode = "$VALIDATION_PREFIX.METHOD_NOT_SUPPORTED"
        )
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse)
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(e: NoHandlerFoundException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 핸들러를 찾을 수 없음: {}", requestInfo, e.requestURL)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "요청한 리소스를 찾을 수 없습니다: ${e.requestURL}",
            errorCode = "$VALIDATION_PREFIX.RESOURCE_NOT_FOUND"
        )
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    // === 도메인 특정 예외 ===
    @ExceptionHandler(kr.hhplus.be.server.domain.user.exception.UserNotFoundException::class)
    fun handleUserNotFoundException(e: kr.hhplus.be.server.domain.user.exception.UserNotFoundException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 사용자 없음: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "사용자를 찾을 수 없습니다",
            errorCode = e.errorCode
        )
        
        return ResponseEntity.status(e.status).body(errorResponse)
    }

    @ExceptionHandler(kr.hhplus.be.server.domain.user.exception.UserAlreadyExistsException::class)
    fun handleUserAlreadyExistsException(e: kr.hhplus.be.server.domain.user.exception.UserAlreadyExistsException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 사용자 이미 존재: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "이미 존재하는 사용자입니다",
            errorCode = e.errorCode
        )
        
        return ResponseEntity.status(e.status).body(errorResponse)
    }

    @ExceptionHandler(kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException::class)
    fun handleTokenNotFoundException(e: kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 토큰 없음: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message,
            errorCode = e.errorCode
        )
        
        return ResponseEntity.status(e.status).body(errorResponse)
    }

    @ExceptionHandler(kr.hhplus.be.server.domain.reservation.exception.ReservationNotFoundException::class)
    fun handleReservationNotFoundException(e: kr.hhplus.be.server.domain.reservation.exception.ReservationNotFoundException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 예약 없음: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "예약을 찾을 수 없습니다",
            errorCode = e.getFullErrorCode()
        )
        
        return ResponseEntity.status(e.httpStatus).body(errorResponse)
    }

    @ExceptionHandler(kr.hhplus.be.server.domain.payment.exception.PaymentException::class)
    fun handlePaymentException(e: kr.hhplus.be.server.domain.payment.exception.PaymentException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 결제 예외: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "결제 처리 중 오류가 발생했습니다",
            errorCode = e.getFullErrorCode()
        )
        
        return ResponseEntity.status(e.httpStatus).body(errorResponse)
    }

    // === 보안 관련 예외 ===
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 접근 권한 없음: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "접근 권한이 없습니다",
            errorCode = "$SECURITY_PREFIX.ACCESS_DENIED"
        )
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
    }

    // === 일반적인 예외 처리 ===
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 잘못된 인수: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "잘못된 요청 파라미터입니다",
            errorCode = "$VALIDATION_PREFIX.INVALID_ARGUMENT"
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        logger.warn("[{}] 잘못된 상태: {}", requestInfo, e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "현재 상태에서는 해당 작업을 수행할 수 없습니다",
            errorCode = "$SYSTEM_PREFIX.INVALID_STATE"
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e: RuntimeException): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        val errorId = generateErrorId()
        logger.error("[{}] 런타임 예외 [ErrorID: {}]: {}", requestInfo, errorId, e.message, e)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "처리 중 오류가 발생했습니다. 에러 ID: $errorId",
            errorCode = "$SYSTEM_PREFIX.RUNTIME_ERROR"
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(e: Exception): ResponseEntity<CommonApiResponse<Nothing>> {
        val requestInfo = getCurrentRequestInfo()
        val errorId = generateErrorId()
        logger.error("[{}] 예상치 못한 예외 [ErrorID: {}]: {}", requestInfo, errorId, e.message, e)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "서버 내부 오류가 발생했습니다. 에러 ID: $errorId",
            errorCode = "$SYSTEM_PREFIX.INTERNAL_ERROR"
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    // === 유틸리티 메서드 ===
    private fun getCurrentRequestInfo(): String {
        return try {
            val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
            val userAgent = request.getHeader("User-Agent")?.take(50) ?: "unknown"
            val clientIp = getClientIpAddress(request)
            "${request.method} ${request.requestURI} [IP: $clientIp] [UA: $userAgent]"
        } catch (e: Exception) {
            "UNKNOWN REQUEST"
        }
    }
    
    private fun getClientIpAddress(request: jakarta.servlet.http.HttpServletRequest): String {
        val headers = listOf(
            "X-Forwarded-For",
            "X-Real-IP", 
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        )
        
        for (header in headers) {
            val ip = request.getHeader(header)
            if (!ip.isNullOrBlank() && !ip.equals("unknown", ignoreCase = true)) {
                return ip.split(",")[0].trim()
            }
        }
        
        return request.remoteAddr ?: "unknown"
    }
    
    private fun generateErrorId(): String {
        return System.currentTimeMillis().toString(36).uppercase() + 
               (1000..9999).random().toString()
    }
}
