package kr.hhplus.be.server.global.lock

/**
 * 분산 락 키를 중앙에서 관리하는 클래스
 * 락 키 충돌 방지 및 일관성 있는 키 네이밍 보장
 */
object LockKeyManager {
    
    // =================================================================
    // 좌석 관련 락 키 (통합 관리)
    // =================================================================
    
    /**
     * 좌석 관련 모든 작업에 대한 통합 락
     * 예약, 확정, 취소 등 모든 좌석 상태 변경 시 사용
     */
    fun seatOperation(seatId: Long): String = "seat:op:$seatId"
    
    // =================================================================
    // 예약 관련 락 키
    // =================================================================
    
    /**
     * 예약 확정 락 (결제 완료 시)
     */
    fun reservationConfirm(reservationId: Long): String = "reservation:confirm:$reservationId"
    
    /**
     * 예약 취소 락
     */
    fun reservationCancel(reservationId: Long): String = "reservation:cancel:$reservationId"
    
    // =================================================================
    // 결제 관련 락 키  
    // =================================================================
    
    /**
     * 결제 처리 전체 플로우 락
     * 한 사용자의 특정 예약에 대한 결제는 동시에 하나만 처리
     * 
     * 중요: PaymentService 내부에서는 다른 서비스의 Internal 메서드를 호출하여
     * 중첩 락을 방지합니다.
     */
    fun paymentProcess(userId: Long, reservationId: Long): String = "payment:process:$userId:$reservationId"
    
    // =================================================================
    // 사용자 잔액 관련 락 키
    // =================================================================
    
    /**
     * 사용자 포인트 잔액 관련 모든 작업
     * 충전, 차감, 조회 등
     */
    fun userBalance(userId: Long): String = "user:balance:$userId"
    
    // =================================================================
    // 토큰 관련 락 키
    // =================================================================
    
    /**
     * 토큰 활성화 락
     */
    fun tokenActivate(token: String): String = "token:activate:$token"
    
    /**
     * 큐 처리 락 (전역 락)
     */
    fun queueProcess(): String = "queue:process:global"
    
    // =================================================================
    // 락 키 유틸리티
    // =================================================================
    
    /**
     * 락 키가 특정 리소스와 관련있는지 확인
     */
    fun isSeatRelated(lockKey: String): Boolean = lockKey.startsWith("seat:")
    fun isUserRelated(lockKey: String): Boolean = lockKey.startsWith("user:")
    fun isPaymentRelated(lockKey: String): Boolean = lockKey.startsWith("payment:")
    fun isReservationRelated(lockKey: String): Boolean = lockKey.startsWith("reservation:")
    
    /**
     * 락 키에서 리소스 ID 추출
     */
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