package kr.hhplus.be.server.domain.concert.aop

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IncrementViewCount(
    val concertIdParam: String = "concertId"
)
