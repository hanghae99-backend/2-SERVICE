package kr.hhplus.be.server.global.lock


object LockKeyManager {
    
    // 좌석의 모든 상태 변경을 하나의 락으로 보호 (예약/확정/취소)
    fun seatOperation(seatId: Long): String = "seat:op:$seatId"
    

    fun reservationConfirm(reservationId: Long): String = "reservation:confirm:$reservationId"
    

    fun reservationCancel(reservationId: Long): String = "reservation:cancel:$reservationId"
    
    // 중첩 락 방지: 결제 플로우 내에서 Internal 메서드 호출로 락 중복 회피
    fun paymentProcess(userId: Long, reservationId: Long): String = "payment:process:$userId:$reservationId"
    

    fun userBalance(userId: Long): String = "user:balance:$userId"
    

    fun tokenActivate(token: String): String = "token:activate:$token"
    

    fun queueProcess(): String = "queue:process:global"
    

    fun isSeatRelated(lockKey: String): Boolean = lockKey.startsWith("seat:")
    fun isUserRelated(lockKey: String): Boolean = lockKey.startsWith("user:")
    fun isPaymentRelated(lockKey: String): Boolean = lockKey.startsWith("payment:")
    fun isReservationRelated(lockKey: String): Boolean = lockKey.startsWith("reservation:")
    
    // 락 키 파싱으로 리소스 ID 추출
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