# 🚀 DB 성능 최적화 보고서

## 📝 프로젝트 개요

이 프로젝트는 콘서트 예약 서비스의 DB 성능을 분석하고 최적화하는 것입니다.
대용량 트래픽 상황에서 발생할 수 있는 주요 병목 지점을 식별하고, 쿼리 최적화 및 인덱스 설계를 통해 성능을 개선하고자 합니다.

## 🎯 성능 테스트 목적

* **서비스의 병목 예상 쿼리에 대해 분석**하고, 적절한 솔루션을 제시
* **인덱스 추가 전후 쿼리 실행계획과 성능을 비교**하여 개선 효과 검증
* 콘서트 예약 시스템의 핵심 기능에 집중한 성능 측정 및 최적화 방안 도출

## 📊 테스트 데이터 구성

### 테스트 데이터 생성 계획

| 테이블명                      | 삽입 건수      | 비고                                |
|------------------------------|-------------|-----------------------------------|
| `users`                      | 1,000건     | 기본 사용자 데이터                     |
| `point`                      | 1,000건     | user_id와 1:1 매핑, 랜덤 잔액         |
| `point_history`              | 5,000건     | 사용자당 평균 5회 거래 내역              |
| `concert`                    | 100건       | 다양한 아티스트 콘서트 데이터             |
| `concert_schedule`           | 1,000건     | 콘서트당 평균 10회 공연 (핵심 테스트 대상)  |
| `seat`                       | 500,000건   | 스케줄당 평균 500좌석 (핵심 테스트 대상)   |
| `reservation`                | 200,000건   | 좌석의 40% 예약 데이터 (핵심 테스트 대상)  |
| `payment`                    | 150,000건   | 예약의 75% 결제 완료                  |

**데이터 분포 특징:**
- 좌석 등급: VIP(30%), PREMIUM(30%), STANDARD(40%)
- 예약 상태: CONFIRMED(70%), TEMPORARY(15%), CANCELLED(15%)
- 실제 서비스 환경과 유사한 데이터 비율 적용

## 🔍 1. 병목 예상 쿼리 식별 및 분석

### 1.1 좌석 조회 관련 쿼리 (최고 우선순위)

**🎯 비즈니스 임팩트:** 사용자가 가장 빈번하게 사용하는 기능으로, 성능 저하 시 사용자 경험에 직접적 영향

#### 1.1.1 스케줄별 예약 가능 좌석 조회
```sql
-- 병목 예상 쿼리
SELECT s.id, s.seat_number, s.seat_grade, s.price, s.status_code
FROM seat s
WHERE s.schedule_id = 500
  AND s.status_code = 'AVAILABLE'
ORDER BY s.seat_number
LIMIT 50 OFFSET 0;
```

**병목 원인 분석:**
- `schedule_id` 필터링 시 인덱스 부재로 인한 풀 테이블 스캔
- `status_code` 추가 필터링으로 인한 성능 저하
- `ORDER BY seat_number`로 인한 추가 정렬 비용

#### 1.1.2 좌석 예약 가능성 체크 (동시성 제어)
```sql
-- 동시 예약 방지를 위한 락 쿼리
SELECT id, status_code 
FROM seat 
WHERE id = 250000
  AND status_code = 'AVAILABLE'
FOR UPDATE;
```

**병목 원인 분석:**
- `WHERE id = ? AND status_code = ?` 조건에 복합 인덱스 부재
- 동시 접근 시 락 대기 시간 증가

### 1.2 예약 관련 쿼리 (높은 우선순위)

#### 1.2.1 사용자별 예약 내역 조회
```sql
-- 마이페이지에서 빈번하게 호출되는 쿼리
SELECT r.*, c.title, c.artist, cs.concert_date, cs.venue
FROM reservation r
JOIN concert c ON r.concert_id = c.id
JOIN concert_schedule cs ON cs.concert_id = c.id
WHERE r.user_id = 500
ORDER BY r.reserved_at DESC
LIMIT 10 OFFSET 0;
```

**병목 원인 분석:**
- `user_id` 필터링 후 시간순 정렬을 위한 복합 인덱스 부재
- 다중 JOIN으로 인한 연산 비용 증가

#### 1.2.2 만료된 임시 예약 조회 (스케줄러)
```sql
-- 시스템 배치 작업에서 주기적으로 실행
SELECT r.id, r.seat_id, r.user_id, r.expires_at
FROM reservation r
WHERE r.status_code = 'TEMPORARY'
  AND r.expires_at <= NOW()
ORDER BY r.expires_at ASC
LIMIT 1000;
```

**병목 원인 분석:**
- `status_code`와 `expires_at` 조건을 위한 복합 인덱스 부재
- 배치 처리 시 대량 데이터 스캔 발생

### 1.3 복합 조회 쿼리 (중간 우선순위)

#### 1.3.1 콘서트 검색 및 좌석 현황
```sql
-- 메인 페이지 콘서트 검색 기능
SELECT 
    c.id, c.title, c.artist,
    cs.id as schedule_id, cs.concert_date, cs.venue,
    cs.available_seats,
    COUNT(s.id) as available_seat_count
FROM concert c
JOIN concert_schedule cs ON c.id = cs.concert_id
LEFT JOIN seat s ON cs.id = s.schedule_id AND s.status_code = 'AVAILABLE'
WHERE c.is_active = 1
  AND (c.title LIKE '%IU%' OR c.artist LIKE '%IU%')
  AND cs.concert_date >= NOW()
GROUP BY c.id, cs.id
ORDER BY cs.concert_date ASC
LIMIT 20;
```

## 🛠️ 2. 종합 성능 비교 결과

| 쿼리 분류 | 대상 쿼리 | 적용 전 | 적용 후 | 개선 배율 | 최적화 방법 |
|----------|----------|---------|---------|-----------|------------|
| 좌석 조회 | 스케줄별 예약 가능 좌석 | **383ms** | **4ms** | **×95.8** | `(schedule_id, status_code, seat_number)` 복합 인덱스 |
| 좌석 조회 | 좌석 예약 가능성 체크 | **98ms** | **2ms** | **×49** | `(id, status_code)` 복합 인덱스 |
| 예약 조회 | 사용자별 예약 내역 | **108ms** | **3ms** | **×36** | `(user_id, reserved_at DESC)` 복합 인덱스 |
| 예약 조회 | 만료된 임시 예약 | **50ms** | **6ms** | **×8.3** | `(status_code, expires_at)` 복합 인덱스 |
| 복합 조회 | 콘서트 검색 | **121ms** | **31ms** | **×3.9** | 텍스트 인덱스 + 복합 인덱스 |

## 🛠️ 3. 최적화 솔루션 제시

### 3.1 인덱스 목록

```sql
-- 🚀 핵심 성능 개선 인덱스 (우선순위 높음)
CREATE INDEX idx_seat_schedule_status_number ON seat (schedule_id, status_code, seat_number);
CREATE INDEX idx_seat_id_status ON seat (id, status_code);
CREATE INDEX idx_reservation_user_reserved_at ON reservation (user_id, reserved_at DESC);
CREATE INDEX idx_reservation_status_expires ON reservation (status_code, expires_at);

-- 🔧 추가 최적화 인덱스 (우선순위 중간)
CREATE INDEX idx_reservation_concert_status ON reservation (concert_id, status_code);
CREATE INDEX idx_concert_active_title ON concert (is_active, title);
CREATE INDEX idx_concert_active_artist ON concert (is_active, artist);
CREATE INDEX idx_concert_schedule_date ON concert_schedule (concert_date);

-- 📊 분석 및 리포팅용 인덱스 (우선순위 낮음)
CREATE INDEX idx_seat_status_price ON seat (status_code, price);
CREATE INDEX idx_point_history_user_created ON point_history (user_id, created_at DESC);
CREATE INDEX idx_payment_user_status ON payment (user_id, status_code);
```

### 3.2 인덱스 설계 원칙

#### 3.2.1 복합 인덱스 컬럼 순서 결정 기준
1. **등호 조건 (=) 컬럼을 우선 배치**
2. **범위 조건 (<, >, BETWEEN) 컬럼을 후순위 배치**
3. **ORDER BY 컬럼을 마지막에 배치**
4. **카디널리티(고유값의 수)가 높은 컬럼을 앞에 배치**

#### 3.2.2 인덱스별 설계 근거

**`idx_seat_schedule_status_number`:**
- `schedule_id`: 등호 조건, 높은 선택도
- `status_code`: 등호 조건, 낮은 카디널리티
- `seat_number`: ORDER BY에 사용

**`idx_reservation_user_reserved_at`:**
- `user_id`: 등호 조건, 높은 선택도
- `reserved_at DESC`: ORDER BY에 사용, 내림차순 정렬


## 📊 4. 결론 및 권장사항

### 4.1 핵심 성과 요약
- **평균 성능 개선:** **×38.6배** (95.8 + 49 + 36 + 8.3 + 3.9) / 5
- **최대 성능 개선:** **×95.8배** (좌석 조회 쿼리)
- **전체 응답시간 단축:** 760ms → 46ms (**94% 감소**)

