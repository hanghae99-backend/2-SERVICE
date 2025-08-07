package kr.hhplus.be.server.domain.user.aop

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValidateUserId(
    val parameterName: String = "userId",
    val nullable: Boolean = false
)
