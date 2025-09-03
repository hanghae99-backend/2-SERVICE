package kr.hhplus.be.server.global.exception

class BusinessRuleViolationException(
    val ruleCode: String,
    val ruleDescription: String,
    val violationDetails: Map<String, Any> = emptyMap()
) : ApplicationException("비즈니스 규칙 위반: $ruleDescription") {
    
    constructor(message: String) : this("GENERAL", message)
    
    fun getViolationDetail(key: String): Any? = violationDetails[key]
    
    fun withDetail(key: String, value: Any): BusinessRuleViolationException {
        return BusinessRuleViolationException(
            ruleCode = this.ruleCode,
            ruleDescription = this.message ?: "Unknown rule violation",
            violationDetails = this.violationDetails + (key to value)
        )
    }
}