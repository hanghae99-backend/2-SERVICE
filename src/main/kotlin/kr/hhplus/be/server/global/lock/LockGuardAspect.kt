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
        
        logger.debug("Acquiring locks with keys: $lockKeys")
        
        return distributedLock.executeWithMultiLock(
            lockKeys = lockKeys,
            strategy = lockGuard.strategy,
            lockTimeoutMs = lockGuard.lockTimeoutMs,
            waitTimeoutMs = lockGuard.waitTimeoutMs,
            retryIntervalMs = lockGuard.retryIntervalMs,
            maxRetryCount = lockGuard.maxRetryCount
        ) {
            logger.debug("Locks acquired, executing method: ${joinPoint.signature.name}")
            val result = joinPoint.proceed()
            logger.debug("Method execution completed, releasing locks: $lockKeys")
            result
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
        
        val context = StandardEvaluationContext()
        
        for (i in parameterNames.indices) {
            context.setVariable(parameterNames[i], args[i])
        }
        
        val exp = parser.parseExpression(expression)
        return exp.getValue(context, String::class.java) ?: expression
    }
}
