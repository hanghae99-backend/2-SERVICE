-- 콘서트 예약 시스템 초기 데이터 (좌석 50개, 10만원 통일)

-- 사용자 데이터
INSERT INTO users (user_id, created_at, updated_at) VALUES
(1, NOW(), NOW()), (2, NOW(), NOW()), (3, NOW(), NOW());

-- 포인트 초기 잔액
INSERT INTO point (user_id, amount, last_updated, created_at) VALUES
(1, 500000.00, NOW(), NOW()),
(2, 300000.00, NOW(), NOW()),
(3, 800000.00, NOW(), NOW());

-- 포인트 히스토리 타입
INSERT INTO point_history_type (code, name, description, is_active, created_at) VALUES
('CHARGE', '충전', '포인트 충전', true, NOW()),
('USE', '사용', '포인트 사용', true, NOW());

-- 포인트 히스토리
INSERT INTO point_history (user_id, amount, type_code, description, created_at) VALUES
(1, 500000.00, 'CHARGE', '초기 포인트 충전', NOW()),
(2, 300000.00, 'CHARGE', '초기 포인트 충전', NOW()),
(3, 800000.00, 'CHARGE', '초기 포인트 충전', NOW());

-- 콘서트 데이터
INSERT INTO concert (title, artist, is_active, created_at, updated_at) VALUES
('IU 2024 HEREH WORLD TOUR', 'IU', true, NOW(), NOW()),
('BTS WORLD TOUR', 'BTS', true, NOW(), NOW());

-- 콘서트 스케줄 (50개 좌석으로 통일)
INSERT INTO concert_schedule (concert_id, concert_date, venue, total_seats, available_seats, created_at) VALUES
(1, '2024-12-15', '올림픽공원 체조경기장', 50, 50, NOW()),
(2, '2024-12-22', '잠실종합운동장', 50, 50, NOW());

-- 좌석 상태 타입
INSERT INTO seat_status_type (code, name, description, is_active, created_at) VALUES
('AVAILABLE', '예약 가능', '예약 가능한 좌석', true, NOW()),
('RESERVED', '임시 예약', '결제 대기 중', true, NOW()),
('OCCUPIED', '예약 완료', '결제 완료', true, NOW());

-- 좌석 데이터 (각 스케줄별 50개, 10만원 통일)
-- IU 콘서트 좌석 (01~50)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT 1, LPAD(n, 2, '0'), 'STANDARD', 100000.00, 'AVAILABLE', NOW(), NOW()
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
      SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION
      SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION
      SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 UNION
      SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION
      SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION SELECT 30 UNION
      SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION
      SELECT 36 UNION SELECT 37 UNION SELECT 38 UNION SELECT 39 UNION SELECT 40 UNION
      SELECT 41 UNION SELECT 42 UNION SELECT 43 UNION SELECT 44 UNION SELECT 45 UNION
      SELECT 46 UNION SELECT 47 UNION SELECT 48 UNION SELECT 49 UNION SELECT 50) numbers;

-- BTS 콘서트 좌석 (01~50)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT 2, LPAD(n, 2, '0'), 'STANDARD', 100000.00, 'AVAILABLE', NOW(), NOW()
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
      SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION
      SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION
      SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 UNION
      SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION
      SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION SELECT 30 UNION
      SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION
      SELECT 36 UNION SELECT 37 UNION SELECT 38 UNION SELECT 39 UNION SELECT 40 UNION
      SELECT 41 UNION SELECT 42 UNION SELECT 43 UNION SELECT 44 UNION SELECT 45 UNION
      SELECT 46 UNION SELECT 47 UNION SELECT 48 UNION SELECT 49 UNION SELECT 50) numbers;

-- 예약/결제 상태 타입
INSERT INTO reservation_status_type (code, name, description, is_active, created_at) VALUES
('TEMPORARY', '임시 예약', '결제 대기 중', true, NOW()),
('CONFIRMED', '예약 확정', '결제 완료', true, NOW()),
('CANCELLED', '예약 취소', '취소됨', true, NOW());

INSERT INTO payment_status_type (code, name, description, is_active, created_at) VALUES
('PEND', '결제 대기', '처리 중', true, NOW()),
('COMP', '결제 완료', '성공', true, NOW()),
('FAIL', '결제 실패', '실패', true, NOW());