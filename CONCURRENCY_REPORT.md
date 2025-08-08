# 콘서트 예약 시스템 동시성 제어 검토 보고서

---

## 선택 기준
#### 🔒 낙관적 락 (Optimistic Lock) 선택 기준
- 충돌 가능성이 낮고, 재시도 허용이 가능한 경우

- 성능이 중요하며, 락으로 인한 대기 최소화가 필요한 경우

- 실패 시 다른 대안(좌석 재선택 등)이 사용자에게 허용되는 경우

#### 🔒 비관적 락 (Pessimistic Lock) 선택 기준
- 충돌 가능성이 높고, 정확한 데이터 정합성이 필수인 경우
- 금전 거래 등에서 실패나 중복이 치명적인 경우
- 동시에 같은 자원을 처리할 때 선점 방지가 반드시 필요한 경우

## 📋 동시성 발생 검토

### 1️⃣ 대기열 토큰 발급

#### 🚨 문제 식별
```
Expected: 101
Actual: 36
```
동시성 테스트를 통한 테스트 결과 100번의 토큰 생성 요청 이후 다음 순번은 101번이 되어야 하지만, 36번으로 나오는 문제 발생.

#### 🔍 분석
**대기열 토큰 발급 로직**
```
토큰 생성 요청 → 이미 발급된 토큰 유무 확인 → 토큰 다음 순번 조회 → 토큰 생성 → 토큰 저장
```

현재 구현되어 있는 대기열 토큰 발급 로직 특성상, 동시에 대기열 토큰 발급 요청이 있는 경우 `queue_tokens` 테이블의 `queueNumber`가 동시성 문제가 발생하여 순차적으로 늘어나지 않는 문제 발생.

#### 🔐 락 선택 기준
**Redis 기반 Atomic 연산 선택 이유:**
- ✅ 순번 관리에 최적화된 구조
- ✅ 고성능 처리 가능
- ✅ 단일 원자적 연산으로 경합 조건 해결
- ✅ 별도 테이블 락 불필요

**Redis 기반 순번 관리**
```kotlin
@Component
class RedisTokenStore {
    fun getNextQueueNumber(): Long {
        return redisTemplate.opsForValue().increment("queue:next:number") ?: 1L
    }
}
```

Redis의 atomic 연산을 활용하여 순번을 안전하게 관리합니다.

---

### 2️⃣ 포인트 충전

#### 🚨 문제 식별
```
Expected: 100,000L
But was: 48,000L
```
동시성 테스트를 통해 100건의 1,000포인트 충전 요청 시 100,000포인트가 최종 보유 포인트가 되어야 하지만, 48,000포인트만 보유하는 문제 발생.

#### 🔍 분석
**포인트 충전 로직**
```
포인트 지갑 조회 → 포인트 충전 → 포인트 지갑 업데이트 → 포인트 내역 저장
```
포인트 지갑 조회 → 포인트 충전 사이에서 동시성 문제(Lost Update)가 발생.

#### 🔐 락 선택 기준  
**✅ 낙관적 락 (Optimistic Lock) 선택 이유:**
- ✅ 데이터에 동시에 접근해 수정하는 빈도가 낮다
- ✅ 충돌이 발생해도 재시도하는 비용이 낮거나 허용된다
- ✅ 시스템이 높은 처리량과 응답 속도를 우선시한다
- ✅ 데드락(deadlock)이 절대 발생하지 않도록 해야 한다

#### 🥕 해결 방안
**JPA 낙관적 락 + 재시도 패턴**
```kotlin
@Entity
class Point(
    @Version
    var version: Long = 0  // JPA 낙관적 락
)

@Service
class ChargeBalanceUseCase {
    companion object {
        const val MAX_RETRIES = 10
        const val BACKOFF_MILLIS = 50L
    }
    
    fun execute(userId: Long, amount: BigDecimal): Point {
        repeat(MAX_RETRIES) { attempt ->
            try {
                return executeChargeInternal(userId, amount)
            } catch (e: OptimisticLockingFailureException) {
                Thread.sleep(BACKOFF_MILLIS * (attempt + 1))
            }
        }
        throw PointException("충전 재시도 실패")
    }
}
```

---

### 3️⃣ 포인트 차감

#### 🚨 문제 식별
동시 차감 시 잔액이 음수가 되거나, 정확하지 않은 차감이 발생할 위험성 존재.

#### 🔍 분석
**포인트 차감 로직**
```
포인트 지갑 조회 → 잔액 검증 → 포인트 차감 → 포인트 지갑 업데이트 → 포인트 내역 저장
```
금전 관련 로직으로 절대적인 정확성이 필요.

#### 🔐 락 선택 기준
**🔒 비관적 락 (Pessimistic Lock) 선택 이유:**
- ✅ 동시에 동일 데이터를 수정할 가능성이 높다
- ✅ 충돌이 발생하면 사용자나 비즈니스에 직접적인 영향이 있다
- ✅ 절대적인 데이터 정합성이 요구된다
- ✅ 금전 관련 로직으로 오차 허용 불가

#### 🥕 해결 방안
**JPA 비관적 락**
```kotlin
@Repository
interface PointJpaRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Point p WHERE p.userId = :userId")
    fun findByUserIdWithPessimisticLock(@Param("userId") userId: Long): Optional<Point>
}

@Service
class DeductBalanceUseCase {
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun execute(userId: Long, amount: BigDecimal): Point {
        val point = pointRepository.findByUserIdWithPessimisticLock(userId)
        return point.deduct(amount) // 잔액 검증 포함
    }
}
```

---

### 4️⃣ 좌석 예약

#### 🚨 문제 식별
```
Expecting empty but was: [1L]
```
동일한 좌석에 대해 동시에 예약 요청이 발생하는 경우 여러 유저에게 동일한 좌석이 예약되는 문제 발생.

#### 🔍 분석
**좌석 예약 로직**
```
콘서트 좌석 조회 → 콘서트 스케줄 조회 → 좌석 점유 확인 → 좌석 점유 상태 업데이트 
→ 콘서트 좌석 상태 업데이트 → 결제 정보 생성 → 예약 생성
```

#### 🔐 락 선택 기준
**✅ 낙관적 락 (Optimistic Lock) 선택 이유:**
- ✅ 최초 요청 이후 요청에 대해서는 전부 실패 처리하면 됨
- ✅ 비관적 락 사용 시 불필요한 데이터베이스 락 발생
- ✅ 사용자는 실패 시 다른 좌석 선택 가능
- ✅ 빠른 응답 속도 우선

#### 🥕 해결 방안
**JPA 낙관적 락**
```kotlin
@Entity
class Seat(
    @Version
    var version: Long = 0  // JPA 낙관적 락
)

@Service
class ReserveSeatUseCase {
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun execute(userId: Long, concertId: Long, seatId: Long, token: String): Reservation {
        try {
            val seat = seatRepository.findById(seatId)
            if (!seat.isAvailable()) {
                throw SeatNotAvailableException("이미 예약된 좌석입니다")
            }
            
            seat.reserve(userId)
            seatRepository.save(seat) // 낙관적 락으로 중복 예약 방지
            
            return createReservation(userId, seat)
        } catch (e: OptimisticLockingFailureException) {
            throw SeatReservationFailedException("좌석 예약에 실패했습니다. 다른 좌석을 선택해주세요.")
        }
    }
}
```

---

### 5️⃣ 예약 결제

#### 🚨 문제 식별
동시에 결제 요청 시 하나의 예약에 중복 결제가 발생할 것으로 예상.

#### 🔍 분석
**예약 결제 로직**
```
예약 내역 조회 → 결제 목록 조회 → 포인트 차감 → 포인트 업데이트 → 포인트 내역 추가 
→ 결제 상태 업데이트 → 예약 상태 업데이트 → 콘서트 좌석 상태 업데이트 → 좌석 점유 제거 → 대기열 토큰 상태 업데이트
```

**🔒 비관적 락 (Pessimistic Lock) 선택 이유:**
- ✅ 동시에 동일 데이터를 수정할 가능성이 높다
- ✅ 데이터 충돌 발생 시 재시도나 롤백이 복잡하거나 위험하다
- ✅ 충돌이 발생하면 사용자나 비즈니스에 직접적인 영향이 있다
- ✅ 절대적인 데이터 정합성이 요구된다
- ✅ 금전 관련 트랜잭션으로 정확성 필수

#### 🥕 해결 방안
**예약과 결제에 비관적 락 적용**
```kotlin
@Entity
class Reservation(
    @Version
    var version: Long = 0
)

@Entity
class Payment(
    @Version
    var version: Long = 0
)

@Service
class ProcessPaymentUseCase {
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun execute(userId: Long, reservationId: Long, token: String): Payment {
        // 예약에 비관적 락 적용
        val reservation = reservationRepository.findByIdWithPessimisticLock(reservationId)
            ?: throw ReservationNotFoundException()
            
        if (reservation.isAlreadyPaid()) {
            throw DuplicatePaymentException("이미 결제된 예약입니다")
        }
        
        // 포인트 차감 (비관적 락)
        pointService.deduct(userId, reservation.totalAmount)
        
        // 결제 생성 및 예약 상태 업데이트
        val payment = Payment.create(reservation)
        reservation.confirmPayment()
        
        return paymentRepository.save(payment)
    }
}
```



