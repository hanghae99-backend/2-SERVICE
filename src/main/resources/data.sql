-- 콘서트 예약 시스템 초기 데이터 (좌석 50개, 10만원 통일)

-- 사용자 데이터
INSERT INTO users (user_id, created_at, updated_at) VALUES
                                                        (1, NOW(), NOW()), (2, NOW(), NOW()), (3, NOW(), NOW()),
                                                        (4, NOW(), NOW()), (5, NOW(), NOW()), (6, NOW(), NOW()),
                                                        (7, NOW(), NOW()), (8, NOW(), NOW()), (9, NOW(), NOW()),
                                                        (10, NOW(), NOW());

-- 포인트 초기 잔액 (updated_at 필드 추가)
INSERT INTO point (user_id, amount, last_updated, created_at, updated_at) VALUES
                                                                              (1, 500000.00, NOW(), NOW(), NOW()),
                                                                              (2, 300000.00, NOW(), NOW(), NOW()),
                                                                              (3, 800000.00, NOW(), NOW(), NOW()),
                                                                              (4, 150000.00, NOW(), NOW(), NOW()),
                                                                              (5, 750000.00, NOW(), NOW(), NOW()),
                                                                              (6, 400000.00, NOW(), NOW(), NOW()),
                                                                              (7, 600000.00, NOW(), NOW(), NOW()),
                                                                              (8, 250000.00, NOW(), NOW(), NOW()),
                                                                              (9, 900000.00, NOW(), NOW(), NOW()),
                                                                              (10, 350000.00, NOW(), NOW(), NOW());

-- 포인트 히스토리 타입
INSERT INTO point_history_type (code, name, description, is_active, created_at) VALUES
                                                                                    ('CHARGE', '충전', '포인트 충전', true, NOW()),
                                                                                    ('USE', '사용', '포인트 사용', true, NOW());

-- 포인트 히스토리
INSERT INTO point_history (user_id, amount, type_code, description, created_at, updated_at) VALUES
                                                                                                (1, 500000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (2, 300000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (3, 800000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (4, 150000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (5, 750000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (6, 400000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (7, 600000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (8, 250000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (9, 900000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW()),
                                                                                                (10, 350000.00, 'CHARGE', '초기 포인트 충전', NOW(), NOW());

-- 콘서트 데이터
INSERT INTO concert (title, artist, is_active, created_at, updated_at) VALUES
                                                                           ('IU 2024 HEREH WORLD TOUR', 'IU', true, NOW(), NOW()),
                                                                           ('BTS WORLD TOUR', 'BTS', true, NOW(), NOW()),
                                                                           ('NewJeans Get Up Tour', 'NewJeans', true, NOW(), NOW()),
                                                                           ('BLACKPINK BORN PINK WORLD TOUR', 'BLACKPINK', true, NOW(), NOW()),
                                                                           ('aespa SYNC TOUR', 'aespa', true, NOW(), NOW()),
                                                                           ('SEVENTEEN GOD OF MUSIC TOUR', 'SEVENTEEN', true, NOW(), NOW()),
                                                                           ('TWICE 5TH WORLD TOUR', 'TWICE', true, NOW(), NOW()),
                                                                           ('LE SSERAFIM FLAME RISES TOUR', 'LE SSERAFIM', true, NOW(), NOW());

-- 콘서트 스케줄 (50개 좌석으로 통일)
INSERT INTO concert_schedule (concert_id, concert_date, venue, total_seats, available_seats, created_at, updated_at) VALUES
                                                                                                                         (1, '2025-08-14', '올림픽공원 체조경기장', 50, 50, NOW(), NOW()),
                                                                                                                         (1, '2025-08-15', '올림픽공원 체조경기장', 50, 50, NOW(), NOW()),
                                                                                                                         (2, '2025-08-16', '잠실종합운동장', 50, 50, NOW(), NOW()),
                                                                                                                         (2, '2025-08-17', '잠실종합운동장', 50, 50, NOW(), NOW()),
                                                                                                                         (3, '2025-08-20', '고체체육관', 50, 50, NOW(), NOW()),
                                                                                                                         (3, '2025-08-21', '고체체육관', 50, 50, NOW(), NOW()),
                                                                                                                         (4, '2025-08-25', 'KSPO DOME', 50, 50, NOW(), NOW()),
                                                                                                                         (4, '2025-08-26', 'KSPO DOME', 50, 50, NOW(), NOW()),
                                                                                                                         (5, '2025-09-01', '오송언신화배용은취 THE K예술처', 50, 50, NOW(), NOW()),
                                                                                                                         (5, '2025-09-02', '오송언신화배용은취 THE K예술처', 50, 50, NOW(), NOW()),
                                                                                                                         (6, '2025-09-08', '잠실종합운동장', 50, 50, NOW(), NOW()),
                                                                                                                         (6, '2025-09-09', '잠실종합운동장', 50, 50, NOW(), NOW()),
                                                                                                                         (7, '2025-09-15', '올림픽공원 체조경기장', 50, 50, NOW(), NOW()),
                                                                                                                         (7, '2025-09-16', '올림픽공원 체조경기장', 50, 50, NOW(), NOW()),
                                                                                                                         (8, '2025-09-22', 'KSPO DOME', 50, 50, NOW(), NOW()),
                                                                                                                         (8, '2025-09-23', 'KSPO DOME', 50, 50, NOW(), NOW());

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

-- 모든 콘서트 스케줄의 좌석 데이터 (schedule_id 2~16에 대해 각각 50개 좌석)
INSERT INTO seat (schedule_id, seat_number, seat_grade, price, status_code, created_at, updated_at)
SELECT s.schedule_id, LPAD(n.n, 2, '0'), 'STANDARD', 100000.00, 'AVAILABLE', NOW(), NOW()
FROM (SELECT 2 schedule_id UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION
      SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION
      SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16) s
CROSS JOIN (
    SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
    SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION
    SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION
    SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 UNION
    SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION
    SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION SELECT 30 UNION
    SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION
    SELECT 36 UNION SELECT 37 UNION SELECT 38 UNION SELECT 39 UNION SELECT 40 UNION
    SELECT 41 UNION SELECT 42 UNION SELECT 43 UNION SELECT 44 UNION SELECT 45 UNION
    SELECT 46 UNION SELECT 47 UNION SELECT 48 UNION SELECT 49 UNION SELECT 50
) n;

-- 예약/결제 상태 타입
INSERT INTO reservation_status_type (code, name, description, is_active, created_at) VALUES
                                                                                         ('TEMPORARY', '임시 예약', '결제 대기 중', true, NOW()),
                                                                                         ('CONFIRMED', '예약 확정', '결제 완료', true, NOW()),
                                                                                         ('CANCELLED', '예약 취소', '취소됨', true, NOW());

INSERT INTO payment_status_type (code, name, description, is_active, created_at) VALUES
                                                                                     ('PEND', '결제 대기', '처리 중', true, NOW()),
                                                                                     ('COMP', '결제 완료', '성공', true, NOW()),
                                                                                     ('FAIL', '결제 실패', '실패', true, NOW());

-- 샘플 예약 데이터 (못진 좌석들은 예약된 상태로 설정)
-- IU 콘서트 첫 번째 날 좌석 못진 상태 설정
UPDATE seat SET status_code = 'OCCUPIED' WHERE schedule_id = 1 AND seat_number IN ('01', '02', '03', '10', '11', '15', '20', '25', '30', '35', '40', '45', '50');

-- BTS 콘서트 첫 번째 날 좌석 못진 상태 설정  
UPDATE seat SET status_code = 'OCCUPIED' WHERE schedule_id = 3 AND seat_number IN ('05', '06', '07', '12', '18', '22', '28', '33', '38', '42', '47');

-- NewJeans 콘서트 첫 번째 날 좌석 못진 상태 설정
UPDATE seat SET status_code = 'OCCUPIED' WHERE schedule_id = 5 AND seat_number IN ('01', '04', '08', '16', '24', '31', '39', '46');

-- BLACKPINK 콘서트 첫 번째 날 좌석 못진 상태 설정
UPDATE seat SET status_code = 'OCCUPIED' WHERE schedule_id = 7 AND seat_number IN ('03', '09', '14', '19', '26', '32', '37', '43', '48');

-- 예약 대기 상태 좌석들 (5분 후 만료)
UPDATE seat SET status_code = 'RESERVED' WHERE schedule_id = 1 AND seat_number IN ('04', '05');
UPDATE seat SET status_code = 'RESERVED' WHERE schedule_id = 3 AND seat_number IN ('08', '09');
UPDATE seat SET status_code = 'RESERVED' WHERE schedule_id = 5 AND seat_number IN ('02', '03');
