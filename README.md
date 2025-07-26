# 콘서트 예약 서비스 (Concert Reservation Service)

## 아키텍처 설계

#### DDD 기반 도메인 분리 아키텍처 (각 도메인 내부는 레이어드 구조)

**구조 개요**:
```
src/main/kotlin/kr/hhplus/be/server/
├── [domain]/              # 도메인별 패키지 분리
│   ├── controller/        # REST API 엔드포인트
│   ├── service/           # 비즈니스 로직
│   ├── repository/        # 데이터 액세스
│   ├── entity/            # 도메인 엔티티
│   └── dto/               # DTO
```
#### 아키텍처 선택 이유
1. 적은 도메인 수 ( 6개의 도메인(auth, user, concert, reservation, payment, balance) )
2. 빠른 개발을 위하여 채택 (단기 프로젝트, 개인 프로젝트)
3. 향후 대규모 확장 가능성이 낮음.
   - 안정적인 기술 스택 (Spring Boot + MySQL + Redis)
   - Repository 인터페이스를 사용하면 디비 같은 경우에는 변경이 크게 어렵지 않음!

#### 추가로 도메인 기반 패키지 분리 선택 이유
1. 도메인 별로 패키지를 분리하는 게 MSA 환경에서 서비스를 분리하기 용이하다. (명확하게 도메인별로 분리해서 개발할 수 있다. 독립성 상승!)
2. 응집도 상승!
3. 실무에서 많이 사용된다! (하지만.. 개인적으로 많이 사용하지 않아, 더욱 도전해보고 싶었다.)

### 프로젝트 구조

```
src/main/kotlin/kr/hhplus/be/server/
├── auth/                    # 대기열 토큰 관리 도메인
│   ├── controller/         # REST API 엔드포인트
│   ├── service/           # 비즈니스 로직 (TokenService, QueueManager)
│   ├── entity/            # 도메인 엔티티 (WaitingToken)
│   ├── dto/               # 요청/응답 DTO
│   ├── repository/        # 데이터 액세스 인터페이스
│   ├── infrastructure/    # Redis 기반 토큰 저장소
│   ├── factory/           # 토큰 생성 팩토리
│   └── exception/         # 도메인 특화 예외
│
├── concert/                # 콘서트 관리 도메인
│   ├── controller/        # 콘서트, 좌석 API
│   ├── service/           # ConcertService, SeatService
│   ├── entity/            # Concert, ConcertSchedule, Seat
│   ├── dto/               # 콘서트 관련 DTO
│   ├── repository/        # JPA Repository
│   └── exception/         # 콘서트 도메인 예외
│
├── reservation/            # 예약 관리 도메인
│   ├── controller/        # 예약 API
│   ├── service/           # ReservationService
│   ├── entity/            # Reservation, ReservationStatusType
│   ├── dto/               # 예약 관련 DTO
│   ├── repository/        # 예약 데이터 액세스
│   └── exception/         # 예약 도메인 예외
│
├── payment/                # 결제 관리 도메인
│   ├── controller/        # 결제 API
│   ├── service/           # PaymentService
│   ├── entity/            # Payment, PaymentStatusType
│   ├── dto/               # 결제 관련 DTO
│   ├── repository/        # 결제 데이터 액세스
│   └── exception/         # 결제 도메인 예외
│
├── balance/                # 잔액 관리 도메인
│   ├── controller/        # 잔액 충전/조회 API
│   ├── service/           # BalanceService
│   ├── entity/            # Point, PointHistory
│   ├── dto/               # 잔액 관련 DTO
│   ├── repository/        # 포인트 데이터 액세스
│   └── exception/         # 잔액 도메인 예외
│
├── user/                   # 사용자 관리 도메인
│   ├── controller/        # 사용자 API
│   ├── service/           # UserService
│   ├── entity/            # User
│   ├── dto/               # 사용자 관련 DTO
│   ├── repository/        # 사용자 데이터 액세스
│   └── exception/         # 사용자 도메인 예외
│
└── global/                 # 공통 관심사
    ├── config/            # 설정 (JPA, Swagger, Redis)
    ├── exception/         # 글로벌 예외 처리
    ├── response/          # 공통 응답 형식
    └── scheduler/         # 배치 작업 (예약 만료 처리)
```

### 도메인 간 상호작용

각 도메인은 서비스 레이어를 통해 상호작용하며, 다음과 같은 의존성을 가집니다:

- **Reservation** → **Auth** (토큰 검증), **Concert** (좌석 정보)
- **Payment** → **Reservation** (예약 확인), **Balance** (잔액 처리), **Auth** (토큰 검증)
- **Auth** → Redis 인프라스트럭처
- **Global** → 모든 도메인 (공통 예외 처리, 응답 형식)
