package kr.hhplus.be.server.global.lock


object LockKeyManager {
    fun seatOperation(seatId: Long): String = "seat:op:$seatId"
    fun reservationConfirm(reservationId: Long): String = "reservation:confirm:$reservationId"
    fun reservationCancel(reservationId: Long): String = "reservation:cancel:$reservationId"
    fun paymentProcess(userId: Long, reservationId: Long): String = "payment:process:$userId:$reservationId"
    fun userBalance(userId: Long): String = "user:balance:$userId"
    fun tokenActivate(token: String): String = "token:activate:$token"
    fun queueProcess(): String = "queue:process:global"
    fun isSeatRelated(lockKey: String): Boolean = lockKey.startsWith("seat:")
    fun isUserRelated(lockKey: String): Boolean = lockKey.startsWith("user:")
    fun isPaymentRelated(lockKey: String): Boolean = lockKey.startsWith("payment:")
    fun isReservationRelated(lockKey: String): Boolean = lockKey.startsWith("reservation:")
    
    fun extractSeatId(lockKey: String): Long? {
        return if (lockKey.startsWith("seat:op:")) {
            lockKey.substringAfterLast(":").toLongOrNull()
        } else null
    }
    
    fun extractUserId(lockKey: String): Long? {
        return if (lockKey.startsWith("user:balance:")) {
            lockKey.substringAfterLast(":").toLongOrNull()
        } else null
    }
}