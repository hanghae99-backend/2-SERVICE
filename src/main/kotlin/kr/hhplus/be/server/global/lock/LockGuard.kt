package kr.hhplus.be.server.global.lock

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LockGuard(
    val key: String,
    val lockTimeoutMs: Long = 10000L,
    val waitTimeoutMs: Long = 5000L
)
