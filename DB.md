# 콘서트 예약 서비스

## 개요
대기열 시스템을 기반으로 한 콘서트 예약 서비스입니다. 다수의 사용자가 동시에 접근할 때 안정적인 서비스를 제공하며, 좌석 예약 시 동시성 문제를 해결합니다.

## 주요 기능
- **대기열 토큰 시스템**: 사용자 순서 관리 및 서비스 접근 제어
- **좌석 예약 시스템**: 임시 배정을 통한 안전한 좌석 예약
- **결제 시스템**: 사용자 잔액 관리 및 결제 처리
- **동시성 제어**: Redis 기반 분산 락으로 멀티 인스턴스 환경에서 안전한 데이터 처리




## API 엔드포인트

### 대기열 관리
- `POST /api/v1/queue/tokens` - 대기열 토큰 발급
- `GET /api/v1/queue/tokens/{token}/status` - 대기열 상태 조회

### 사용자 관리
- `POST /api/users?userId={userId}` - 사용자 생성

### 잔액 관리
- `POST /api/balance/charge` - 잔액 충전
- `GET /api/balance/{userId}` - 잔액 조회
- `GET /api/balance/history/{userId}` - 포인트 내역 조회

### 콘서트 관리
- `GET /api/concerts/available` - 예약 가능한 콘서트 목록 조회
- `GET /api/concerts/by-date?date={date}` - 특정 날짜 콘서트 조회
- `GET /api/concerts/{concertId}` - 콘서트 상세 정보 조회

### 좌석 관리
- `GET /api/concerts/{concertId}/seats/available` - 예약 가능한 좌석 목록 조회
- `GET /api/concerts/{concertId}/seats` - 모든 좌석 정보 조회
- `GET /api/concerts/seats/{seatId}` - 특정 좌석 정보 조회

### 예약 관리
- `POST /api/concerts/reservations` - 좌석 예약
- `GET /api/concerts/reservations/user/{userId}` - 사용자 예약 목록 조회
- `GET /api/concerts/reservations/{reservationId}` - 특정 예약 정보 조회

### 결제 관리
- `POST /api/payments` - 결제 처리
- `GET /api/payments/history/{userId}` - 결제 내역 조회
- `GET /api/payments/{paymentId}` - 특정 결제 정보 조회

## 시퀀스 다이어그램

### 1. 대기열 토큰 발급 및 상태 확인
```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant API as API 서버
    participant TS as TokenService
    participant QM as QueueManager
    participant Redis as Redis
    
    C->>API: POST /api/v1/queue/tokens
    API->>TS: issueWaitingToken(userId)
    TS->>TS: validateUserId()
    TS->>TS: createWaitingToken()
    TS->>Redis: saveToken()
    TS->>QM: addToQueue(token)
    TS->>API: WaitingToken
    API->>C: TokenIssueResponse
    
    loop 대기 중
        C->>API: GET /api/v1/queue/tokens/{token}/status
        API->>TS: getTokenStatus(token)
        TS->>Redis: getTokenStatus()
        TS->>QM: getQueuePosition()
        TS->>API: TokenStatusResponse
        API->>C: QueueStatusResponse
    end
```

### 2. 좌석 예약 프로세스
```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant API as API 서버
    participant RS as ReservationService
    participant TS as TokenService
    participant SS as SeatService
    participant DB as 데이터베이스
    
    C->>API: POST /api/concerts/reservations
    API->>RS: reserveSeat(userId, concertId, seatId, token)
    RS->>TS: validateActiveToken(token)
    TS->>Redis: 토큰 검증
    TS->>RS: 검증 완료
    
    RS->>SS: validateSeatAvailability(seatId)
    SS->>DB: 좌석 상태 확인
    SS->>RS: 좌석 사용 가능
    
    RS->>DB: 예약 생성 (5분 임시 예약)
    RS->>SS: 좌석 상태 변경
    RS->>API: Reservation
    API->>C: SeatReservationResponse
```

### 3. 결제 프로세스
```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant API as API 서버
    participant PS as PaymentService
    participant BS as BalanceService
    participant RS as ReservationService
    participant TS as TokenService
    participant DB as 데이터베이스
    
    C->>API: POST /api/payments
    API->>PS: processPayment(userId, reservationId, token)
    PS->>TS: validateActiveToken(token)
    PS->>RS: validateReservation(reservationId)
    
    PS->>BS: validateBalance(userId, amount)
    BS->>DB: 잔액 확인
    
    PS->>BS: deductBalance(userId, amount)
    BS->>DB: 잔액 차감 및 히스토리 생성
    
    PS->>RS: confirmReservation(reservationId)
    RS->>DB: 예약 확정
    
    PS->>DB: 결제 정보 저장
    PS->>TS: completeReservation(token)
    PS->>API: Payment
    API->>C: PaymentResponse
```

## 클래스 다이어그램

### 핵심 서비스 클래스
```mermaid
classDiagram
    class TokenService {
        +issueWaitingToken(userId) WaitingToken
        +getTokenStatus(token) TokenStatusResponse
        +validateActiveToken(token) WaitingToken
        +completeReservation(token) void
        +processQueueAutomatically() void
    }
    
    class QueueManager {
        +addToQueue(token) void
        +getQueueStatus(token) QueueStatusResponse
        +getQueuePosition(token) int
        +processQueueAutomatically() void
        +calculateAvailableSlots() int
    }
    
    class TokenLifecycleManager {
        +saveToken(token) void
        +findToken(token) WaitingToken?
        +getTokenStatus(token) TokenStatus
        +completeToken(token) void
        +cleanupExpiredTokens() void
    }
    
    class ReservationService {
        +reserveSeat(userId, concertId, seatId, token) Reservation
        +getReservationById(id) Reservation
        +getReservationsByUserId(userId) List~Reservation~
        +confirmReservation(id) Reservation
    }
    
    class PaymentService {
        +processPayment(userId, reservationId, token) Payment
        +getPaymentHistory(userId) List~Payment~
        +getPaymentById(id) Payment
    }
    
    class BalanceService {
        +chargeBalance(userId, amount) Point
        +getBalance(userId) Point
        +validateBalance(userId, amount) boolean
        +deductBalance(userId, amount) Point
        +getPointHistory(userId) List~PointHistory~
    }
    
    class ConcertService {
        +getAvailableConcerts(startDate, endDate) List~ConcertSchedule~
        +getConcertsByDate(date) List~ConcertSchedule~
        +getConcertById(id) ConcertSchedule
    }
    
    class SeatService {
        +getAvailableSeats(concertId) List~SeatInfo~
        +getAllSeats(concertId) List~SeatInfo~
        +getSeatById(id) SeatInfo
        +validateSeatAvailability(seatId) boolean
    }
    
    TokenService --> QueueManager
    TokenService --> TokenLifecycleManager
    ReservationService --> TokenService
    ReservationService --> SeatService
    PaymentService --> TokenService
    PaymentService --> BalanceService
    PaymentService --> ReservationService
```

## 데이터베이스 스키마 (ERD)

```mermaid
erDiagram
    USER {
        bigint user_id PK
        timestamp created_at
        timestamp updated_at
    }
    
    WAITING_TOKEN_REDIS {
        varchar token PK "Redis Key"
        bigint user_id
    }
    
    CONCERT {
        bigint id PK
        varchar title
        varchar artist
        timestamp created_at
        timestamp updated_at
    }
    
    CONCERT_SCHEDULE {
        bigint id PK
        bigint concert_id FK
        date concert_date
        varchar venue
        int total_seats
        int available_seats
        timestamp created_at
    }
    
    SEAT {
        bigint id PK
        bigint concert_id FK
        varchar seat_number
        decimal price
        varchar status_code FK
        timestamp created_at
        timestamp updated_at
    }
    
    SEAT_STATUS_TYPE {
        varchar code PK
        varchar name
        varchar description
        boolean is_active
        timestamp created_at
    }
    
    RESERVATION {
        bigint id PK
        bigint user_id FK
        bigint concert_id FK
        bigint seat_id FK
        bigint payment_id FK
        varchar seat_number
        decimal price
        varchar status_code FK
        timestamp reserved_at
        timestamp expires_at
        timestamp confirmed_at
    }
    
    RESERVATION_STATUS_TYPE {
        varchar code PK
        varchar name
        varchar description
        boolean is_active
        timestamp created_at
    }
    
    PAYMENT {
        bigint id PK
        bigint user_id FK
        decimal amount
        varchar status_code FK
        varchar payment_method
        timestamp paid_at
    }
    
    PAYMENT_STATUS_TYPE {
        varchar code PK
        varchar name
        varchar description
        boolean is_active
        timestamp created_at
    }
    
    POINT {
        bigint id PK
        bigint user_id FK
        decimal amount
        timestamp last_updated
        timestamp created_at
    }
    
    POINT_HISTORY {
        bigint id PK
        bigint user_id FK
        decimal amount
        varchar type_code FK
        varchar description
        timestamp created_at
    }
    
    POINT_HISTORY_TYPE {
        varchar code PK
        varchar name
        varchar description
        boolean is_active
        timestamp created_at
    }
    
    USER ||--o{ POINT : "has"
    USER ||--o{ POINT_HISTORY : "has"
    USER ||--o{ RESERVATION : "makes"
    USER ||--o{ PAYMENT : "makes"
    CONCERT ||--o{ CONCERT_SCHEDULE : "has"
    CONCERT ||--o{ SEAT : "contains"
    CONCERT_SCHEDULE ||--o{ RESERVATION : "for"
    SEAT ||--o{ RESERVATION : "reserved"
    PAYMENT ||--o{ RESERVATION : "completes"
    
    SEAT_STATUS_TYPE ||--o{ SEAT : "defines"
    RESERVATION_STATUS_TYPE ||--o{ RESERVATION : "defines"
    PAYMENT_STATUS_TYPE ||--o{ PAYMENT : "defines"
    POINT_HISTORY_TYPE ||--o{ POINT_HISTORY : "defines"
```
```
