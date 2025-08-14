package kr.hhplus.be.server.global.lock

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LockGuard(
    val key: String = "",
    val keys: Array<String> = [],
    val strategy: LockStrategy = LockStrategy.SPIN,
    val lockTimeoutMs: Long = 10000L,
    val waitTimeoutMs: Long = 5000L,
    val retryIntervalMs: Long = 50L,
    val maxRetryCount: Int = 100
)
