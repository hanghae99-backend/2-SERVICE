package kr.hhplus.be.server.global.lock


object LockKeyManager {
    // 기본 자원별 락 키
    fun seat(seatId: Long): String = "seat:$seatId"
    fun reservation(reservationId: Long): String = "reservation:$reservationId"
    fun user(userId: Long): String = "user:$userId"
    fun payment(paymentId: Long): String = "payment:$paymentId"
    
    // 특수한 경우들
    fun queueProcess(): String = "queue:process:global"
    
    // 키 타입 검증
    fun isSeatRelated(lockKey: String): Boolean = lockKey.startsWith("seat:")
    fun isUserRelated(lockKey: String): Boolean = lockKey.startsWith("user:")
    fun isPaymentRelated(lockKey: String): Boolean = lockKey.startsWith("payment:")
    fun isReservationRelated(lockKey: String): Boolean = lockKey.startsWith("reservation:")
    
    fun extractSeatId(lockKey: String): Long? {
        return if (lockKey.startsWith("seat:")) {
            lockKey.substringAfter(":").toLongOrNull()
        } else null
    }
    
    fun extractUserId(lockKey: String): Long? {
        return if (lockKey.startsWith("user:")) {
            lockKey.substringAfter(":").toLongOrNull()
        } else null
    }
    
    fun extractReservationId(lockKey: String): Long? {
        return if (lockKey.startsWith("reservation:")) {
            lockKey.substringAfter(":").toLongOrNull()
        } else null
    }
    
    fun extractPaymentId(lockKey: String): Long? {
        return if (lockKey.startsWith("payment:")) {
            lockKey.substringAfter(":").toLongOrNull()
        } else null
    }
}