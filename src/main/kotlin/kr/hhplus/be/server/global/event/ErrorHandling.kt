package kr.hhplus.be.server.global.event

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventErrorHandling(
    val sendToDLQ: Boolean = true,
    val maxRetries: Int = 3,
    val critical: Boolean = false
)