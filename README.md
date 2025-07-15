# 콘서트 예약 서비스

## 개요
대기열 시스템을 기반으로 한 콘서트 예약 서비스입니다. 다수의 사용자가 동시에 접근할 때 안정적인 서비스를 제공하며, 좌석 예약 시 동시성 문제를 해결합니다.

## 주요 기능
- **대기열 토큰 시스템**: 사용자 순서 관리 및 서비스 접근 제어
- **좌석 예약 시스템**: 임시 배정을 통한 안전한 좌석 예약 (1~50번 좌석)
- **결제 시스템**: 사용자 잔액 관리 및 결제 처리
- **동시성 제어**: 멀티 인스턴스 환경에서 안전한 데이터 처리


## 시퀀스 다이어그램

### 1. 대기열 토큰 발급 및 활성화
```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant API as API 서버
    participant TS as 토큰서비스
    participant R as Redis
    participant DB as 데이터베이스
    
    C->>API: POST /api/v1/queue/tokens
    API->>TS: 대기토큰발급(사용자ID)
    TS->>DB: 최대순서조회()
    TS->>TS: 다음순서생성()
    TS->>R: 토큰저장(대기토큰)
    TS->>API: 대기토큰
    API->>C: 토큰응답
    
    loop 대기 중
        C->>API: GET /api/v1/queue/tokens/{token}/position
        API->>TS: 현재순서조회(토큰)
        TS->>R: 토큰조회(토큰)
        TS->>TS: 예상대기시간계산()
        TS->>API: 토큰순서응답
        API->>C: 대기 정보
    end
    
    C->>API: PATCH /api/v1/queue/tokens/{token}/activate
    API->>TS: 사용자활성화(토큰)
    TS->>TS: 활성화조건검증()
    TS->>R: 토큰상태변경(활성)
    TS->>API: 활성화된토큰
    API->>C: 활성화 완료
```

### 2. 좌석 예약 프로세스
```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant API as API 서버
    participant TS as 토큰서비스
    participant RS as 예약서비스
    participant SS as 좌석서비스
    participant R as Redis
    participant DB as 데이터베이스
    
    C->>API: POST /api/v1/reservations
    API->>TS: 토큰검증(토큰)
    TS->>R: 활성토큰조회(토큰)
    TS->>API: 토큰유효
    
    API->>SS: 좌석가용성확인(좌석ID)
    SS->>DB: 좌석조회(좌석ID)
    SS->>API: 좌석가용
    
    API->>RS: 예약생성(사용자ID, 좌석ID)
    RS->>SS: 좌석예약(좌석ID, 사용자ID)
    SS->>DB: 좌석상태변경(예약됨)
    SS->>R: 예약만료시간설정(좌석ID, 5분)
    RS->>DB: 예약정보저장()
    RS->>API: 예약생성완료
    API->>C: 예약 완료
    
    Note over R: 5분 후 자동 만료
    R->>SS: 예약만료처리(좌석ID)
    SS->>DB: 좌석상태변경(예약가능)
```

### 3. 결제 프로세스
```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant API as API 서버
    participant PS as 결제서비스
    participant BS as 잔액서비스
    participant RS as 예약서비스
    participant TS as 토큰서비스
    participant DB as 데이터베이스
    
    C->>API: POST /api/v1/payments
    API->>TS: 토큰검증(토큰)
    TS->>API: 토큰유효
    
    API->>PS: 결제처리(예약ID)
    PS->>RS: 예약정보조회(예약ID)
    RS->>PS: 예약상세정보
    
    PS->>BS: 잔액확인(사용자ID, 금액)
    BS->>DB: 사용자잔액조회(사용자ID)
    BS->>PS: 잔액확인완료
    
    PS->>BS: 잔액차감(사용자ID, 금액)
    BS->>DB: 잔액업데이트(사용자ID, -금액)
    
    PS->>RS: 예약확정(예약ID)
    RS->>DB: 예약상태변경(확정)
    
    PS->>TS: 토큰만료(토큰)
    TS->>DB: 토큰상태변경(만료)
    
    PS->>DB: 결제정보저장()
    PS->>API: 결제성공
    API->>C: 결제 완료
```

## 클래스 다이어그램

### 핵심 도메인 클래스
```mermaid
classDiagram
    class TokenDomainService {
        +예상대기시간계산(순서, 현재활성수, 최대활성수) Long
        +다음순서생성(현재최대순서) Long
        +활성화가능여부(토큰, 현재활성수, 최대활성수) Boolean
        +최대활성토큰수조회() Long
    }
    
    class TokenValidationService {
        +활성화가능여부(토큰) Boolean
        +만료가능여부(토큰) Boolean
        +서비스이용가능여부(토큰) Boolean
        +유효한순서여부(순서) Boolean
    }
    
    class TokenApplicationService {
        +대기토큰발급(사용자ID) 대기토큰
        +현재순서조회(토큰) 토큰순서응답
        +사용자활성화(토큰) 대기토큰
        +토큰만료(토큰) 대기토큰
        +활성토큰수조회() Long
    }
    
    class ReservationService {
        +예약생성(사용자ID, 좌석ID) 예약
        +예약확정(예약ID) 예약
        +예약취소(예약ID) 예약
        +예약조회(예약ID) 예약
    }
    
    class PaymentService {
        +결제처리(예약ID) 결제
        +결제내역조회(사용자ID) List
        +결제환불(결제ID) 결제
    }
    
    class BalanceService {
        +잔액충전(사용자ID, 금액) 잔액
        +잔액조회(사용자ID) 잔액
        +잔액차감(사용자ID, 금액) 잔액
    }
    
    TokenApplicationService --> TokenDomainService
    TokenApplicationService --> TokenValidationService
    ReservationService --> TokenValidationService
    PaymentService --> TokenValidationService
    PaymentService --> BalanceService
```

## 데이터베이스 스키마 (ERD)

```mermaid
erDiagram
    USER {
        uuid user_id PK
        varchar name
        varchar email
        timestamp created_at
        timestamp updated_at
    }
    
    WAITING_TOKEN {
        varchar token PK
        uuid user_id FK
        bigint position
        enum status
        timestamp issued_at
        timestamp activated_at
        timestamp expired_at
    }
    
    CONCERT {
        bigint concert_id PK
        varchar title
        varchar artist
        varchar venue
        date concert_date
        time start_time
        time end_time
        decimal base_price
        timestamp created_at
        timestamp updated_at
    }
    
    SEAT {
        bigint seat_id PK
        bigint concert_id FK
        int seat_number
        decimal price
        enum status
        timestamp created_at
        timestamp updated_at
    }
    
    RESERVATION {
        bigint reservation_id PK
        uuid user_id FK
        bigint seat_id FK
        enum status
        timestamp created_at
        timestamp expires_at
        timestamp confirmed_at
    }
    
    PAYMENT {
        bigint payment_id PK
        uuid user_id FK
        bigint reservation_id FK
        decimal amount
        enum status
        timestamp paid_at
        timestamp created_at
    }
    
    POINT {
        bigint point_id PK
        uuid user_id FK
        decimal amount
        timestamp last_updated
        timestamp created_at
    }
    
    POINT_HISTORY {
        bigint history_id PK
        uuid user_id FK
        decimal amount
        enum type
        varchar description
        timestamp created_at
    }
    
    USER ||--o{ WAITING_TOKEN : "has"
    USER ||--o{ POINT : "has"
    USER ||--o{ POINT_HISTORY : "has"
    USER ||--o{ RESERVATION : "makes"
    USER ||--o{ PAYMENT : "makes"
    CONCERT ||--o{ SEAT : "contains"
    SEAT ||--o{ RESERVATION : "reserved"
    RESERVATION ||--o{ PAYMENT : "paid_by"
```


