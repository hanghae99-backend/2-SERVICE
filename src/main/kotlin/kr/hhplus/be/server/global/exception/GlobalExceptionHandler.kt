package kr.hhplus.be.server.global.exception

import kr.hhplus.be.server.global.lock.ConcurrentAccessException
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<CommonApiResponse<Nothing>> {
        logger.warn("Business exception occurred: {}", e.message, e)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "알 수 없는 비즈니스 오류가 발생했습니다",
            errorCode = e.getFullErrorCode()
        )
        
        return ResponseEntity.status(e.httpStatus).body(errorResponse)
    }

    @ExceptionHandler(ConcurrentAccessException::class)
    fun handleConcurrentAccessException(e: ConcurrentAccessException): ResponseEntity<CommonApiResponse<Nothing>> {
        logger.warn("Concurrent access exception: {}", e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "요청이 집중되어 처리할 수 없습니다. 잠시 후 다시 시도해주세요",
            errorCode = "SYSTEM.CONCURRENT_ACCESS"
        )
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<CommonApiResponse<Nothing>> {
        logger.warn("Illegal argument exception: {}", e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "잘못된 요청 파라미터입니다",
            errorCode = "SYSTEM.INVALID_ARGUMENT"
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException): ResponseEntity<CommonApiResponse<Nothing>> {
        logger.warn("Illegal state exception: {}", e.message)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = e.message ?: "현재 상태에서는 해당 작업을 수행할 수 없습니다",
            errorCode = "SYSTEM.INVALID_STATE"
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(e: Exception): ResponseEntity<CommonApiResponse<Nothing>> {
        logger.error("Unexpected exception occurred", e)
        
        val errorResponse = CommonApiResponse.error<Nothing>(
            message = "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요",
            errorCode = "SYSTEM.INTERNAL_ERROR"
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    private fun getCurrentPath(): String {
        return try {
            val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
            request.requestURI
        } catch (e: Exception) {
            "unknown"
        }
    }
}
