# STEP14-16: 이벤트 드리븐 비동기 시스템 설계 및 트랜잭션 진단

## 📋 개요

콘서트 예약 시스템에서 **이벤트 드리븐 아키텍처**를 기반으로 한 비동기 처리 시스템을 설계하고 구현한 프로젝트입니다. 핵심 로직과 부가 로직의 분리, 실시간 데이터 플랫폼 연동, 분산 트랜잭션 관리를 통한 시스템 안정성 향상이 목표입니다.

---

## 🎯 STEP 14: 이벤트 드리븐 비동기 시스템

### 1. 핵심 로직과 부가 로직의 적절한 분리

#### 1.1 이벤트 기반 아키텍처 설계 원칙


```
📦 핵심 로직 (Core Logic) - 동기 처리
 ┣ 📄 예약 생성 (좌석 예약, DB 저장)
 ┣ 📄 결제 처리 (잔액 차감, 결제 완료)
 ┣ 📄 토큰 발급 (대기열 관리)
 ┗ 📄 좌석 상태 변경

📦 부가 로직 (Side Logic) - 비동기 처리
 ┣ 📄 매진 랭킹 업데이트
 ┣ 📄 데이터 플랫폼 연동
 ┣ 📄 통계 데이터 수집
 ┗ 📄 외부 시스템 알림
```

#### 1.2 실제 구현: 예약 생성 서비스

핵심 로직은 **동기적으로 처리**하여 즉시 응답하고, 부가 로직은 **이벤트 발행**을 통해 비동기로 분리합니다.

```kotlin
@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val eventPublisher: DomainEventPublisher,
    private val seatApiClient: SeatApiClient
) {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun createReservation(userId: Long, concertId: Long, seatId: Long): Reservation {
        // 1. 핵심 로직 - 동기 처리 (실패 시 즉시 중단)
        seatApiClient.validateSeatAvailability(seatId)
        val seatInfo = seatApiClient.getSeatInfo(seatId)
        val reservation = createReservationWithSeatInfo(userId, concertId, seatId, seatInfo.seatNumber, seatInfo.price)
        seatApiClient.reserveSeat(seatId)

        // 2. 이벤트 발행 - 부가 로직 트리거 (핵심 로직과 분리)
        eventPublisher.publish(ReservationCreatedEvent(
            reservationId = reservation.reservationId,
            userId = reservation.userId,
            concertId = reservation.concertId,
            seatId = reservation.seatId,
            seatNumber = reservation.seatNumber,
            price = reservation.price,
            expiresAt = reservation.expiresAt
        ))

        return reservation // 핵심 로직 완료 후 즉시 응답
    }
}
```

#### 1.3 부가 로직 이벤트 핸들러 구현

**매진 랭킹 업데이트 (핵심 로직과 완전 분리)**
```kotlin
@Component
class SelloutRankingOnReservationCreatedListener(
    private val selloutRankingService: SelloutRankingService,
    private val errorHandler: EventErrorHandler
) {

    @EventErrorHandling(sendToDLQ = false, critical = false)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ReservationCreatedEvent) {
        errorHandler.handleEventSafely(event, "ReservationCreatedEvent") {
            selloutRankingService.incrementReservationCount(event.concertId)
            logger.info { "매진 순위 업데이트 완료 - concertId: ${event.concertId}" }
        }
    }
}
```

**데이터 플랫폼 연동 (실시간 정보 전송)**
```kotlin
@Component
class DataPlatformReservationCreatedListener(
    private val concertDataPlatformClient: ConcertDataPlatformClient,
    private val errorHandler: EventErrorHandler
) {

    @EventErrorHandling(sendToDLQ = false, critical = false)
    @Async
    @EventListener
    fun handle(event: ReservationCreatedEvent) {
        errorHandler.handleEventSafely(event, "ReservationCreatedEvent") {
            concertDataPlatformClient.sendReservationData(
                reservationId = event.reservationId,
                userId = event.userId,
                concertId = event.concertId,
                seatId = event.seatId,
                operationType = "RESERVATION_CREATED"
            )
            logger.info { "데이터 플랫폼 예약 생성 정보 전송 완료: ${event.reservationId}" }
        }
    }
}
```


### 2. 트랜잭션 분리에 따른 문제점 및 해결방안

#### 2.1 분산 트랜잭션 문제점 분석

| 문제 유형 | 발생 시나리오 | 현재 시스템에서의 대응 방안 |
|----------|--------------|---------------------------|
| **데이터 일관성** | 결제 성공 후 예약 업데이트 실패 | **보상 트랜잭션** 이벤트 발행 |
| **부분 실패** | 포인트 차감 성공, 예약 실패 | **PaymentFailedEvent**로 잔액 복구 |
| **네트워크 장애** | 서비스 간 통신 실패 | **재시도 메커니즘** 및 **DLQ** 처리 |


#### 2.2 실제 구현된 보상 트랜잭션 패턴

결제 실패 시 **자동으로 보상 트랜잭션**이 실행되어 데이터 일관성 유지.

```kotlin
@Service
class PaymentService(
    private val eventPublisher: DomainEventPublisher
) {

    @Transactional
    fun processPayment(userId: Long, reservationId: Long, token: String, amount: BigDecimal): PaymentDto {
        try {
            // 1. 결제 생성
            val payment = createReservationPayment(userId, reservationId, amount)

            // 2. 잔액 차감 (외부 도메인 호출)
            balanceApiClient.deductBalance(userId, amount, "콘서트 티켓 결제")

            // 3. 예약 확정 (외부 도메인 호출)
            reservationApiClient.confirmReservation(reservationId, payment.paymentId)

            // 4. 결제 완료 처리
            val completedPayment = completePayment(payment)

            // 5. 성공 이벤트 발행
            eventPublisher.publish(PaymentCompletedEvent(
                paymentId = completedPayment.paymentId,
                userId = userId,
                reservationId = reservationId,
                amount = completedPayment.amount,
                token = token
            ))

            return PaymentDto.fromEntity(completedPayment)

        } catch (e: Exception) {
            // 실패 시 보상 트랜잭션 이벤트 발행
            val failedPayment = createFailedPayment(userId, reservationId, amount, e.message ?: "알 수 없는 오류")

            eventPublisher.publish(PaymentFailedEvent(
                paymentId = failedPayment.paymentId,
                userId = userId,
                reservationId = reservationId,
                reason = e.message ?: "결제 처리 실패",
                token = token,
                amount = amount,
                needsBalanceRestore = true, // 잔액 복구 필요
                failureStage = determineFailureStage(e)
            ))

            throw PaymentProcessException("결제 처리 중 오류가 발생했습니다: ${e.message}", e)
        }
    }
}
```

#### 2.3 보상 트랜잭션 이벤트 핸들러

결제 실패 이벤트를 받아 잔액을 복구.

```kotlin
@Component
class BalanceRestorationOnPaymentFailedListener(
    private val balanceApiClient: BalanceApiClient
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentFailedEvent) {
        if (event.needsBalanceRestore && event.amount != null) {
            try {
                // 보상 트랜잭션: 차감된 잔액 복구
                balanceApiClient.restoreBalance(
                    userId = event.userId,
                    amount = event.amount,
                    description = "결제 실패로 인한 잔액 복구 - 예약ID: ${event.reservationId}"
                )
                logger.info { "잔액 복구 완료 - userId: ${event.userId}, amount: ${event.amount}" }
            } catch (e: Exception) {
                logger.error(e) { "잔액 복구 실패 - userId: ${event.userId}, amount: ${event.amount}" }
            }
        }
    }
}
```