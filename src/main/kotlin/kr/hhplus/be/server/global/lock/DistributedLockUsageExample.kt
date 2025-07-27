package kr.hhplus.be.server.global.lock

/**
 * Redis 분산 락 사용 예제 및 테스트 코드
 */
class DistributedLockUsageExample {
    
    /**
     * 기본 사용법
     */
    fun basicUsage(distributedLock: DistributedLock) {
        val result = distributedLock.executeWithLock(
            lockKey = "user:balance:123",
            lockTimeoutMs = 10000L,
            waitTimeoutMs = 5000L
        ) {
            // 임계 영역에서 실행할 코드
            "작업 완료"
        }
        println("결과: $result")
    }
    
    /**
     * 좌석 예약에서의 사용 예제
     */
    fun seatReservationExample(distributedLock: DistributedLock) {
        val seatId = 123L
        val lockKey = "seat:reservation:$seatId"
        
        try {
            val reservation = distributedLock.executeWithLock(
                lockKey = lockKey,
                lockTimeoutMs = 10000L,
                waitTimeoutMs = 5000L
            ) {
                // 1. 좌석 상태 확인
                // 2. 예약 가능 여부 검증
                // 3. 예약 생성
                // 4. 좌석 상태 업데이트
                "예약 생성됨"
            }
            println("예약 성공: $reservation")
        } catch (e: ConcurrentAccessException) {
            println("동시 접근으로 인한 예약 실패: ${e.message}")
            // 사용자에게 재시도 안내
        }
    }
    
    /**
     * 포인트 충전/사용에서의 사용 예제
     */
    fun pointOperationExample(distributedLock: DistributedLock) {
        val userId = 456L
        val lockKey = "user:balance:$userId"
        
        try {
            val newBalance = distributedLock.executeWithLock(
                lockKey = lockKey,
                lockTimeoutMs = 10000L,
                waitTimeoutMs = 5000L
            ) {
                // 1. 현재 잔액 조회
                // 2. 충전/차감 가능 여부 검증
                // 3. 잔액 업데이트
                // 4. 히스토리 기록
                100000 // 새로운 잔액
            }
            println("잔액 업데이트 성공: $newBalance")
        } catch (e: ConcurrentAccessException) {
            println("동시 접근으로 인한 잔액 업데이트 실패: ${e.message}")
        }
    }
}