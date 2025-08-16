package kr.hhplus.be.server.global.exception

import org.springframework.http.HttpStatus

abstract class BusinessException(
    message: String,
    val errorCode: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    
    abstract val domain: String
    
    fun getFullErrorCode(): String = "${domain}.${errorCode}"
}

abstract class DomainException(
    override val domain: String,
    message: String,
    errorCode: String,
    httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : BusinessException(message, errorCode, httpStatus, cause)
