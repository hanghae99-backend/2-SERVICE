package kr.hhplus.be.server.global.client

import mu.KotlinLogging
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import kotlin.math.pow

object HttpClientUtil {
    
    private val logger = KotlinLogging.logger {}
    private const val DEFAULT_MAX_RETRIES = 3
    private val DEFAULT_RETRY_DELAYS = listOf(50L, 100L, 200L)
    
    fun <T> executeRequest(
        restTemplate: RestTemplate,
        operation: String,
        request: () -> T
    ): T {
        return try {
            request()
        } catch (e: Exception) {
            logger.error(e) { "$operation 실패" }
            throw e
        }
    }
    
    fun <T> executeWithRetry(
        restTemplate: RestTemplate,
        operation: String,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        retryDelays: List<Long> = DEFAULT_RETRY_DELAYS,
        request: () -> T
    ): T {
        repeat(maxRetries) { attempt ->
            try {
                val result = request()
                if (attempt > 0) {
                    logger.info { "$operation 성공 (${attempt + 1}번째 시도)" }
                }
                return result
            } catch (e: HttpClientErrorException) {
                if (shouldRetry(e, attempt, maxRetries)) {
                    val delay = retryDelays.getOrElse(attempt) { retryDelays.last() }
                    logger.warn { "$operation 재시도 - ${attempt + 1}/${maxRetries}번째 시도, ${delay}ms 후 재시도" }
                    Thread.sleep(delay)
                } else {
                    logger.error(e) { "$operation 실패 - 시도: ${attempt + 1}" }
                    throw e
                }
            } catch (e: Exception) {
                logger.error(e) { "$operation 실패 - 시도: ${attempt + 1}" }
                throw e
            }
        }
        throw RuntimeException("$operation 실패 - 최대 재시도 횟수($maxRetries) 초과")
    }
    
    fun <T> get(
        restTemplate: RestTemplate,
        url: String,
        responseType: Class<T>,
        operation: String = "GET 요청"
    ): T {
        return executeRequest(restTemplate, operation) {
            val response = restTemplate.exchange(url, HttpMethod.GET, null, responseType)
            validateResponse(response, operation)
        }
    }
    
    fun <T> get(
        restTemplate: RestTemplate,
        url: String,
        responseType: ParameterizedTypeReference<T>,
        operation: String = "GET 요청"
    ): T {
        return executeRequest(restTemplate, operation) {
            val response = restTemplate.exchange(url, HttpMethod.GET, null, responseType)
            validateResponse(response, operation)
        }
    }
    
    fun <T> post(
        restTemplate: RestTemplate,
        url: String,
        requestBody: Any?,
        responseType: Class<T>,
        operation: String = "POST 요청"
    ): T {
        return executeRequest(restTemplate, operation) {
            val entity = requestBody?.let { HttpEntity(it) }
            val response = restTemplate.exchange(url, HttpMethod.POST, entity, responseType)
            validateResponse(response, operation)
        }
    }
    
    fun <T> put(
        restTemplate: RestTemplate,
        url: String,
        requestBody: Any?,
        responseType: Class<T>,
        operation: String = "PUT 요청"
    ): T {
        return executeRequest(restTemplate, operation) {
            val entity = requestBody?.let { HttpEntity(it) }
            val response = restTemplate.exchange(url, HttpMethod.PUT, entity, responseType)
            validateResponse(response, operation)
        }
    }
    
    fun <T> getWithRetry(
        restTemplate: RestTemplate,
        url: String,
        responseType: Class<T>,
        operation: String = "GET 요청",
        maxRetries: Int = DEFAULT_MAX_RETRIES
    ): T {
        return executeWithRetry(restTemplate, operation, maxRetries) {
            val response = restTemplate.exchange(url, HttpMethod.GET, null, responseType)
            validateResponse(response, operation)
        }
    }
    
    fun <T> postWithRetry(
        restTemplate: RestTemplate,
        url: String,
        requestBody: Any?,
        responseType: Class<T>,
        operation: String = "POST 요청",
        maxRetries: Int = DEFAULT_MAX_RETRIES
    ): T {
        return executeWithRetry(restTemplate, operation, maxRetries) {
            val entity = requestBody?.let { HttpEntity(it) }
            val response = restTemplate.exchange(url, HttpMethod.POST, entity, responseType)
            validateResponse(response, operation)
        }
    }
    
    private fun <T> validateResponse(response: ResponseEntity<T>, operation: String): T {
        if (response.statusCode.is2xxSuccessful && response.body != null) {
            return response.body!!
        } else {
            throw RuntimeException("$operation 실패 - HTTP ${response.statusCode}")
        }
    }
    
    private fun shouldRetry(e: HttpClientErrorException, attempt: Int, maxRetries: Int): Boolean {
        return (e.statusCode.value() == 429 || e.statusCode.is5xxServerError) && attempt < maxRetries - 1
    }
}