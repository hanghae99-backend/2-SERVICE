package kr.hhplus.be.server.global.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(1)
class LockGuardAspect(
    private val distributedLock: DistributedLock
) {
    private val logger = LoggerFactory.getLogger(LockGuardAspect::class.java)
    private val parser: ExpressionParser = SpelExpressionParser()
    
    @Around("@annotation(lockGuard)")
    fun executeWithLock(joinPoint: ProceedingJoinPoint, lockGuard: LockGuard): Any? {
        val lockKeys = if (lockGuard.keys.isNotEmpty()) {
            lockGuard.keys.map { generateLockKey(joinPoint, it) }.sorted() // 정렬로 데드락 방지
        } else if (lockGuard.key.isNotEmpty()) {
            listOf(generateLockKey(joinPoint, lockGuard.key))
        } else {
            throw IllegalArgumentException("LockGuard에 key 또는 keys를 지정해야 합니다")
        }
        
        logger.info("🔒 분산락 시작 - 키: $lockKeys, 전략: ${lockGuard.strategy}, 메소드: ${joinPoint.signature.name}")
        val startTime = System.currentTimeMillis()
        
        return try {
            distributedLock.executeWithMultiLock(
                lockKeys = lockKeys,
                strategy = lockGuard.strategy,
                lockTimeoutMs = lockGuard.lockTimeoutMs,
                waitTimeoutMs = lockGuard.waitTimeoutMs,
                retryIntervalMs = lockGuard.retryIntervalMs,
                maxRetryCount = lockGuard.maxRetryCount
            ) {
                val lockAcquiredTime = System.currentTimeMillis()
                logger.info("✅ 락 획득 성공 - 키: $lockKeys, 대기시간: ${lockAcquiredTime - startTime}ms")
                
                val result = joinPoint.proceed()
                
                val executionTime = System.currentTimeMillis()
                logger.info("🏁 비즈니스 로직 완료 - 키: $lockKeys, 실행시간: ${executionTime - lockAcquiredTime}ms")
                
                result
            }
        } catch (e: Exception) {
            val failTime = System.currentTimeMillis()
            logger.error("❌ 분산락 실패 - 키: $lockKeys, 총 시간: ${failTime - startTime}ms, 에러: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        } finally {
            val totalTime = System.currentTimeMillis()
            logger.info("🔓 분산락 종료 - 키: $lockKeys, 총 소요시간: ${totalTime - startTime}ms")
        }
    }
    
    private fun generateLockKey(joinPoint: ProceedingJoinPoint, keyExpression: String): String {
        return if (keyExpression.contains("#")) {
            evaluateSpelExpression(joinPoint, keyExpression)
        } else {
            keyExpression
        }
    }
    
    private fun evaluateSpelExpression(joinPoint: ProceedingJoinPoint, expression: String): String {
        val methodSignature = joinPoint.signature as MethodSignature
        val parameterNames = methodSignature.parameterNames
        val args = joinPoint.args
        
        logger.debug("SpEL 표현식 평가 시작: $expression")
        logger.debug("메소드: ${joinPoint.signature.name}")
        logger.debug("파라미터 이름들: ${parameterNames.joinToString(", ")}")
        logger.debug("파라미터 값들: ${args.joinToString(", ")}")
        
        val context = StandardEvaluationContext()
        
        for (i in parameterNames.indices) {
            context.setVariable(parameterNames[i], args[i])
            logger.debug("변수 설정: ${parameterNames[i]} = ${args[i]}")
        }
        
        try {
            val exp = parser.parseExpression(expression)
            val result = exp.getValue(context, String::class.java) ?: expression
            logger.debug("SpEL 표현식 결과: $result")
            return result
        } catch (e: Exception) {
            logger.error("SpEL 표현식 평가 실패: $expression", e)
            logger.error("사용 가능한 변수들: ${parameterNames.joinToString(", ")}")
            throw e
        }
    }
}
