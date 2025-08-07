package kr.hhplus.be.server.global.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

@Aspect
@Component
class LockGuardAspect(
    private val distributedLock: DistributedLock
) {
    private val logger = LoggerFactory.getLogger(LockGuardAspect::class.java)
    private val parser: ExpressionParser = SpelExpressionParser()
    
    @Around("@annotation(lockGuard)")
    fun executeWithLock(joinPoint: ProceedingJoinPoint, lockGuard: LockGuard): Any? {
        val lockKey = generateLockKey(joinPoint, lockGuard.key)
        
        logger.debug("Acquiring lock with key: $lockKey")
        
        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = lockGuard.lockTimeoutMs,
            waitTimeoutMs = lockGuard.waitTimeoutMs
        ) {
            logger.debug("Lock acquired, executing method: ${joinPoint.signature.name}")
            val result = joinPoint.proceed()
            logger.debug("Method execution completed, releasing lock: $lockKey")
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
