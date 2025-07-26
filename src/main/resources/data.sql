-- =============================================================================
-- 콘서트 예약 시스템 초기 데이터
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 사용자 데이터
-- -----------------------------------------------------------------------------
INSERT INTO users (user_id, created_at, updated_at) VALUES
(1,  NOW(), NOW()),
(2,  NOW(), NOW()),
(3,  NOW(), NOW()),
(4,  NOW(), NOW()),
(5,  NOW(), NOW());

-- -----------------------------------------------------------------------------
-- 2. 포인트 초기 잔액
-- -----------------------------------------------------------------------------
INSERT INTO point (user_id, amount, last_updated, created_at) VALUES
(1, 500000.00, NOW(), NOW()),
(2, 300000.00, NOW(), NOW()),
(3, 800000.00, NOW(), NOW()),
(4, 200000.00, NOW(), NOW()),
(5, 1000000.00, NOW(), NOW());

-- -----------------------------------------------------------------------------
-- 3. 포인트 히스토리 타입 (코드 테이블)
-- -----------------------------------------------------------------------------
INSERT INTO point_history_type (code, name, description, is_active, created_at) VALUES
('CHARGE', '충전', '포인트 충전', true, NOW()),
('USE', '사용', '포인트 사용', true, NOW());

-- -----------------------------------------------------------------------------
-- 4. 포인트 히스토리 (충전 기록)
-- -----------------------------------------------------------------------------
INSERT INTO point_history (user_id, amount, type_code, description, created_at) VALUES
(1, 500000.00, 'CHARGE', '초기 포인트 충전', NOW()),
(2, 300000.00, 'CHARGE', '초기 포인트 충전', NOW()),
(3, 800000.00, 'CHARGE', '초기 포인트 충전', NOW()),
(4, 200000.00, 'CHARGE', '초기 포인트 충전', NOW()),
(5, 1000000.00, 'CHARGE', '초기 포인트 충전', NOW());

-- -----------------------------------------------------------------------------
-- 5. 콘서트 데이터
-- -----------------------------------------------------------------------------
INSERT INTO concert (title, artist, is_active, created_at, updated_at) VALUES
('IU 2024 HEREH WORLD TOUR CONCERT', 'IU (아이유)', true, NOW(), NOW()),
('BTS WORLD TOUR', 'BTS', true, NOW(), NOW()),
('검정치마 2024 겨울 콘서트', '검정치마', true, NOW(), NOW()),
('NewJeans Get Up Tour', 'NewJeans', true, NOW(), NOW()),
('SEVENTEEN WORLD TOUR', 'SEVENTEEN', true, NOW(), NOW());

-- -----------------------------------------------------------------------------
-- 6. 콘서트 스케줄 데이터
-- -----------------------------------------------------------------------------
INSERT INTO concert_schedule (concert_id, concert_date, venue, total_seats, available_seats, created_at) VALUES
-- IU 콘서트 (2일 연속)
(1, '2024-12-15', '올림픽공원 체조경기장', 100, 95, NOW()),
(1, '2024-12-16', '올림픽공원 체조경기장', 100, 98, NOW()),
-- BTS 콘서트
(2, '2024-12-22', '잠실종합운동장 주경기장', 100, 90, NOW()),
-- 검정치마 콘서트
(3, '2024-12-28', '홍대 V홀', 50, 48, NOW()),
-- NewJeans 콘서트
(4, '2025-01-10', 'KSPO DOME', 100, 100, NOW()),
-- SEVENTEEN 콘서트
(5, '2025-01-20', '고척스카이돔', 100, 100, NOW());

-- -----------------------------------------------------------------------------
-- 7. 좌석 상태 타입 (코드 테이블)
-- -----------------------------------------------------------------------------
INSERT INTO seat_status_type (code, name, description, is_active, created_at) VALUES
('AVAILABLE', '예약 가능', '예약 가능한 좌석', true, NOW()),
('RESERVED', '임시 예약', '결제 대기 중인 임시 예약 좌석', true, NOW()),
('OCCUPIED', '예약 완료', '결제 완료된 좌석', true, NOW()),
('MAINTENANCE', '정비 중', '정비 중인 좌석', true, NOW());

-- -----------------------------------------------------------------------------
-- 8. 좌석 데이터 생성 (각 스케줄별로)
-- -----------------------------------------------------------------------------

-- IU 콘서트 12/15 좌석 (schedule_id = 1)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT 1, 
       CONCAT('A', LPAD(seq, 3, '0')) as seat_number,
       'VIP' as seat_grade,
       180000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row := @row + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row := 0) r
     ) numbers WHERE seq <= 30
UNION ALL
SELECT 1, 
       CONCAT('B', LPAD(seq, 3, '0')) as seat_number,
       'STANDARD' as seat_grade,
       150000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row2 := @row2 + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row2 := 0) r
     ) numbers WHERE seq <= 70;

-- IU 콘서트 12/16 좌석 (schedule_id = 2)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT 2, 
       CONCAT('A', LPAD(seq, 3, '0')) as seat_number,
       'VIP' as seat_grade,
       180000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row3 := @row3 + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row3 := 0) r
     ) numbers WHERE seq <= 30
UNION ALL
SELECT 2, 
       CONCAT('B', LPAD(seq, 3, '0')) as seat_number,
       'STANDARD' as seat_grade,
       150000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row4 := @row4 + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row4 := 0) r
     ) numbers WHERE seq <= 70;

-- BTS 콘서트 좌석 (schedule_id = 3)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT 3, 
       CONCAT('VIP', LPAD(seq, 2, '0')) as seat_number,
       'VIP' as seat_grade,
       250000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row5 := @row5 + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row5 := 0) r
     ) numbers WHERE seq <= 50
UNION ALL
SELECT 3, 
       CONCAT('R', LPAD(seq, 2, '0')) as seat_number,
       'STANDARD' as seat_grade,
       200000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row6 := @row6 + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row6 := 0) r
     ) numbers WHERE seq <= 50;

-- 검정치마 콘서트 좌석 (schedule_id = 4)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT 4, 
       LPAD(seq, 2, '0') as seat_number,
       'STANDARD' as seat_grade,
       80000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row7 := @row7 + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row7 := 0) r
     ) numbers WHERE seq <= 50;

-- NewJeans 콘서트 좌석 (schedule_id = 5)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT 5, 
       CONCAT('P', LPAD(seq, 2, '0')) as seat_number,
       'PREMIUM' as seat_grade,
       120000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row8 := @row8 + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row8 := 0) r
     ) numbers WHERE seq <= 100;

-- SEVENTEEN 콘서트 좌석 (schedule_id = 6)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT 6, 
       CONCAT('S', LPAD(seq, 2, '0')) as seat_number,
       'STANDARD' as seat_grade,
       140000.00 as price,
       'AVAILABLE' as status_code,
       NOW() as created_at,
       NOW() as updated_at
FROM (SELECT @row9 := @row9 + 1 as seq FROM 
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
      (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
       SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
      (SELECT @row9 := 0) r
     ) numbers WHERE seq <= 100;

-- -----------------------------------------------------------------------------
-- 9. 예약 상태 타입 데이터 (참조용)
-- (실제로는 상수로 처리하지만 참조 무결성을 위해)
-- -----------------------------------------------------------------------------
-- CREATE TABLE IF NOT EXISTS reservation_status_type (
--     code VARCHAR(50) PRIMARY KEY,
--     name VARCHAR(100) NOT NULL,
--     description VARCHAR(255),
--     is_active BOOLEAN NOT NULL DEFAULT true,
--     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
-- );
-- 
-- INSERT INTO reservation_status_type (code, name, description, is_active, created_at) VALUES
-- ('TEMP', '임시 예약', '결제 대기 중인 임시 예약', true, NOW()),
-- ('CONF', '예약 확정', '결제 완료된 확정 예약', true, NOW()),
-- ('CANC', '예약 취소', '취소된 예약', true, NOW());

-- -----------------------------------------------------------------------------
-- 10. 테스트용 예약 데이터 (일부 좌석 예약됨 상태)
-- -----------------------------------------------------------------------------

-- 일부 좌석을 임시 예약 상태로 변경
UPDATE seat SET status_code = 'RESERVED', updated_at = NOW() 
WHERE schedule_id = 1 AND seat_number IN ('A001', 'A002', 'B001', 'B002', 'B003');

-- available_seats 수량 조정
UPDATE concert_schedule SET available_seats = available_seats - 5 WHERE id = 1;

-- 일부 좌석을 예약 완료 상태로 변경
UPDATE seat SET status_code = 'OCCUPIED', updated_at = NOW() 
WHERE schedule_id = 3 AND seat_number IN ('VIP01', 'VIP02', 'VIP03', 'R01', 'R02', 'R03', 'R04', 'R05', 'R06', 'R07');

-- available_seats 수량 조정
UPDATE concert_schedule SET available_seats = available_seats - 10 WHERE id = 3;

-- 검정치마 콘서트 일부 좌석 예약됨
UPDATE seat SET status_code = 'RESERVED', updated_at = NOW() 
WHERE schedule_id = 4 AND seat_number IN ('01', '02');

-- available_seats 수량 조정
UPDATE concert_schedule SET available_seats = available_seats - 2 WHERE id = 4;

-- -----------------------------------------------------------------------------
-- 11. 테스트용 결제 및 예약 완료 데이터
-- -----------------------------------------------------------------------------

-- 완료된 결제 데이터
INSERT INTO payment (user_id, amount, payment_method, status_code, paid_at) VALUES
(1, 360000.00, 'POINT', 'COMP', NOW() - INTERVAL 1 DAY),
(2, 450000.00, 'POINT', 'COMP', NOW() - INTERVAL 2 DAY);

-- 완료된 예약 데이터
INSERT INTO reservation (user_id, concert_id, seat_id, payment_id, seat_number, price, status_code, reserved_at, confirmed_at) VALUES
(1, 1, (SELECT id FROM seat WHERE schedule_id = 3 AND seat_number = 'VIP01' LIMIT 1), 1, 'VIP01', 250000.00, 'CONF', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(1, 1, (SELECT id FROM seat WHERE schedule_id = 3 AND seat_number = 'VIP02' LIMIT 1), 1, 'VIP02', 250000.00, 'CONF', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(2, 2, (SELECT id FROM seat WHERE schedule_id = 3 AND seat_number = 'VIP03' LIMIT 1), 2, 'VIP03', 250000.00, 'CONF', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
(2, 2, (SELECT id FROM seat WHERE schedule_id = 3 AND seat_number = 'R01' LIMIT 1), 2, 'R01', 200000.00, 'CONF', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY);

-- 포인트 사용 히스토리 추가
INSERT INTO point_history (user_id, amount, type_code, description, created_at) VALUES
(1, 500000.00, 'USE', 'BTS 콘서트 VIP석 2매 결제', NOW() - INTERVAL 1 DAY),
(2, 450000.00, 'USE', 'BTS 콘서트 VIP석 1매 + 일반석 1매 결제', NOW() - INTERVAL 2 DAY);

-- 포인트 잔액 차감
UPDATE point SET amount = amount - 500000.00, last_updated = NOW() - INTERVAL 1 DAY WHERE user_id = 1;
UPDATE point SET amount = amount - 450000.00, last_updated = NOW() - INTERVAL 2 DAY WHERE user_id = 2;

-- =============================================================================
-- 추가 타입 데이터 (예약 상태, 결제 상태)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 12. 예약 상태 타입 (중복 체크 후 삽입)
-- -----------------------------------------------------------------------------
INSERT INTO reservation_status_type (code, name, description, is_active, created_at)
SELECT 'TEMPORARY', '임시 예약', '결제 대기 중인 임시 예약 상태입니다. 5분 후 자동 만료됩니다.', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reservation_status_type WHERE code = 'TEMPORARY');

INSERT INTO reservation_status_type (code, name, description, is_active, created_at)
SELECT 'CONFIRMED', '예약 확정', '결제가 완료되어 확정된 예약 상태입니다.', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reservation_status_type WHERE code = 'CONFIRMED');

INSERT INTO reservation_status_type (code, name, description, is_active, created_at)
SELECT 'CANCELLED', '예약 취소', '사용자가 취소하거나 만료된 예약 상태입니다.', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reservation_status_type WHERE code = 'CANCELLED');

-- -----------------------------------------------------------------------------
-- 13. 결제 상태 타입 (중복 체크 후 삽입)
-- -----------------------------------------------------------------------------
INSERT INTO payment_status_type (code, name, description, is_active, created_at)
SELECT 'PENDING', '결제 대기', '결제 요청이 처리 대기 중인 상태입니다.', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM payment_status_type WHERE code = 'PENDING');

INSERT INTO payment_status_type (code, name, description, is_active, created_at)
SELECT 'COMPLETED', '결제 완료', '결제가 성공적으로 완료된 상태입니다.', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM payment_status_type WHERE code = 'COMPLETED');

INSERT INTO payment_status_type (code, name, description, is_active, created_at)
SELECT 'FAILED', '결제 실패', '결제 처리 중 오류가 발생한 상태입니다.', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM payment_status_type WHERE code = 'FAILED');

INSERT INTO payment_status_type (code, name, description, is_active, created_at)
SELECT 'CANCELLED', '결제 취소', '사용자 또는 시스템에 의해 취소된 상태입니다.', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM payment_status_type WHERE code = 'CANCELLED');

INSERT INTO payment_status_type (code, name, description, is_active, created_at)
SELECT 'REFUNDED', '결제 환불', '결제가 환불 처리된 상태입니다.', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM payment_status_type WHERE code = 'REFUNDED');

-- -----------------------------------------------------------------------------
-- 14. 타입 데이터 확인
-- -----------------------------------------------------------------------------
SELECT '=== 타입 데이터 확인 ===' as info;

SELECT 'point_history_type' as table_name, code, name FROM point_history_type
UNION ALL
SELECT 'seat_status_type' as table_name, code, name FROM seat_status_type
UNION ALL
SELECT 'reservation_status_type' as table_name, code, name FROM reservation_status_type
UNION ALL
SELECT 'payment_status_type' as table_name, code, name FROM payment_status_type
ORDER BY table_name, code;
