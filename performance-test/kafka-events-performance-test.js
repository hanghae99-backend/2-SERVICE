import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 성능 메트릭 정의
const tokenEventRate = new Rate('token_event_success_rate');
const paymentEventRate = new Rate('payment_event_success_rate');
const reservationEventRate = new Rate('reservation_event_success_rate');

const tokenResponseTime = new Trend('token_event_response_time');
const paymentResponseTime = new Trend('payment_event_response_time');
const reservationResponseTime = new Trend('reservation_event_response_time');

// 테스트 환경 설정
const BASE_URL = 'http://localhost:8080';

// HTTP 연결 안정성 설정
const HTTP_TIMEOUT = '10s';
const MAX_RETRIES = 2;

// 테스트 시나리오 옵션
export let options = {
    scenarios: {
        // 토큰 이벤트 발행 테스트 (안정성 중심)
        token_events: {
            executor: 'ramping-vus',
            exec: 'tokenEventTest',
            startVUs: 2,
            stages: [
                { duration: '30s', target: 5 },
                { duration: '60s', target: 10 },
                { duration: '30s', target: 5 },
                { duration: '20s', target: 0 },
            ],
        },
        
        // 예약 및 결제 이벤트 테스트 (제한된 부하)
        reservation_payment_events: {
            executor: 'ramping-vus',
            exec: 'reservationPaymentEventTest',
            startVUs: 1,
            stages: [
                { duration: '30s', target: 3 },
                { duration: '60s', target: 5 },
                { duration: '30s', target: 3 },
                { duration: '20s', target: 0 },
            ],
            startTime: '15s', // 토큰 이벤트 테스트 후 시작
        },
        
        // 혼합 이벤트 부하 테스트 (낮은 부하)
        mixed_events_stress: {
            executor: 'ramping-vus',
            exec: 'mixedEventsTest',
            startVUs: 1,
            stages: [
                { duration: '30s', target: 3 },
                { duration: '60s', target: 5 },
                { duration: '30s', target: 3 },
                { duration: '20s', target: 0 },
            ],
            startTime: '90s', // 앞선 테스트들 후 시작
        }
    },
    thresholds: {
        // 토큰 이벤트 임계값 (연결 안정성 고려)
        token_event_success_rate: ['rate>0.85'],
        token_event_response_time: ['p(95)<5000'],
        
        // 결제 이벤트 임계값 (더 관대하게 설정)
        payment_event_success_rate: ['rate>0.70'],
        payment_event_response_time: ['p(95)<10000'],
        
        // 예약 이벤트 임계값 (더 관대하게 설정)
        reservation_event_success_rate: ['rate>0.70'],
        reservation_event_response_time: ['p(95)<8000'],
        
        // 전체 HTTP 요청 임계값 (연결 실패 고려)
        http_req_duration: ['p(95)<10000'],
        http_req_failed: ['rate<0.3'],
    },
};

// 테스트 데이터 생성 함수들

// 사용자 ID 생성 방식을 선택하는 설정
const USE_EXISTING_USERS = true; // true: 기존 사용자(1~10) 사용, false: 새 사용자 생성
const EXISTING_USER_RANGE = { min: 1, max: 10 }; // 초기 데이터의 사용자 범위
let nextNewUserId = 1000; // 새 사용자 생성 시 시작 ID

function generateUserId() {
    if (USE_EXISTING_USERS) {
        // 기존 사용자 중 랜덤 선택 (1~10번)
        return Math.floor(Math.random() * (EXISTING_USER_RANGE.max - EXISTING_USER_RANGE.min + 1)) + EXISTING_USER_RANGE.min;
    } else {
        // 새 사용자 ID 생성 (1000번부터)
        return nextNewUserId++;
    }
}

// 사용자를 생성하고 사용자 ID를 반환하는 함수
function ensureUserExists(userId) {
    if (USE_EXISTING_USERS) {
        // 기존 사용자 사용시 생성하지 않음
        return userId;
    }
    
    // 새 사용자 생성
    const userPayload = JSON.stringify({
        userId: userId
    });
    
    const userParams = {
        headers: { 'Content-Type': 'application/json' },
    };
    
    const userResponse = http.post(`${BASE_URL}/api/v1/users`, userPayload, userParams);
    
    if (userResponse.status === 201) {
        try {
            const responseBody = JSON.parse(userResponse.body);
            const userData = responseBody.data;
            console.log(`사용자 생성 성공: userId=${userData.userId}`);
            return userData.userId;
        } catch (e) {
            console.log(`사용자 생성 응답 파싱 실패: userId=${userId}`);
            return userId;
        }
    } else {
        console.log(`사용자 생성 실패: userId=${userId}, status=${userResponse.status}`);
        return null;
    }
}

function generateConcertId() {
    // 콘서트 ID 1~8번 중 랜덤 선택
    return Math.floor(Math.random() * 8) + 1;
}

function generateScheduleId() {
    // 콘서트 스케줄 ID 1~16번 중 랜덤 선택 (각 콘서트당 2개 스케줄)
    return Math.floor(Math.random() * 16) + 1;
}

function generateValidSeat() {
    // 유효한 스케줄과 좌석 번호 조합 생성
    const scheduleId = Math.floor(Math.random() * 16) + 1; // 1~16 스케줄
    const seatNumber = String(Math.floor(Math.random() * 50) + 1).padStart(2, '0'); // 01~50 좌석
    
    // 실제 seat ID를 추정 (schedule_id와 seat_number 기반)
    // Schedule 1: seat IDs 1-50, Schedule 2: seat IDs 51-100, etc.
    const estimatedSeatId = (scheduleId - 1) * 50 + parseInt(seatNumber);
    
    return {
        scheduleId: scheduleId,
        seatNumber: seatNumber,
        seatId: estimatedSeatId
    };
}

function generateAmount() {
    // 모든 좌석이 10만원으로 고정
    return 100000;
}

// 1. 토큰 이벤트 발행 테스트
export function tokenEventTest() {
    const userId = generateUserId();
    
    // 사용자 존재 확인/생성
    if (!USE_EXISTING_USERS) {
        const actualUserId = ensureUserExists(userId);
        if (!actualUserId) {
            console.log(`사용자 생성 실패로 토큰 테스트 중단: userId=${userId}`);
            return;
        }
    }
    
    // 토큰 발급 요청
    const tokenPayload = JSON.stringify({
        userId: userId
    });
    
    const tokenParams = {
        headers: { 'Content-Type': 'application/json' },
    };
    
    const tokenStart = Date.now();
    const tokenResponse = http.post(`${BASE_URL}/api/v1/tokens`, tokenPayload, tokenParams);
    const tokenDuration = Date.now() - tokenStart;
    
    // 토큰 이벤트 성공률 및 응답시간 기록
    const tokenSuccess = check(tokenResponse, {
        'token event status is 201': (r) => r.status === 201,
        'token event has token field': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.token !== undefined && body.data.token !== null;
            } catch (e) {
                return false;
            }
        },
        'token event response time < 3s': () => tokenDuration < 3000,
    });
    
    tokenEventRate.add(tokenSuccess);
    tokenResponseTime.add(tokenDuration);
    
    if (tokenSuccess && tokenResponse.status === 201) {
        try {
            const responseBody = JSON.parse(tokenResponse.body);
            const tokenData = responseBody.data;
            console.log(`토큰 발급 성공: userId=${userId}, token=${tokenData.token}, 응답시간=${tokenDuration}ms`);
        } catch (e) {
            console.log(`토큰 파싱 실패: userId=${userId}, 응답시간=${tokenDuration}ms`);
        }
    } else {
        console.log(`토큰 발급 실패: userId=${userId}, status=${tokenResponse.status}, 응답시간=${tokenDuration}ms`);
    }
    
    sleep(1);
}

// 2. 예약 및 결제 이벤트 테스트
export function reservationPaymentEventTest() {
    const userId = generateUserId();
    const seatInfo = generateValidSeat();
    const concertId = seatInfo.scheduleId; // 스케줄 ID를 concertId로 사용
    const amount = generateAmount();
    
    // 사용자 존재 확인/생성
    if (!USE_EXISTING_USERS) {
        const actualUserId = ensureUserExists(userId);
        if (!actualUserId) {
            console.log(`사용자 생성 실패로 예약/결제 테스트 중단: userId=${userId}`);
            return;
        }
    }
    
    // 1단계: 토큰 발급
    const tokenPayload = JSON.stringify({ userId: userId });
    const tokenParams = { headers: { 'Content-Type': 'application/json' } };
    
    const tokenResponse = http.post(`${BASE_URL}/api/v1/tokens`, tokenPayload, tokenParams);
    if (tokenResponse.status !== 201) {
        console.log(`토큰 발급 실패로 테스트 중단: userId=${userId}`);
        return;
    }
    
    let token;
    try {
        const responseBody = JSON.parse(tokenResponse.body);
        const tokenData = responseBody.data;
        token = tokenData.token;
    } catch (e) {
        console.log(`토큰 파싱 실패로 테스트 중단: userId=${userId}`);
        return;
    }
    
    // 토큰이 카프카를 통해 활성화될 때까지 대기
    sleep(3);
    
    // 2단계: 좌석 예약 (예약 이벤트 발생)
    const reservationPayload = JSON.stringify({
        userId: userId,      // 필수 필드 추가
        concertId: concertId,
        seatId: seatInfo.seatId,
        token: token         // Authorization 헤더 대신 body에 포함
    });
    
    const reservationParams = {
        headers: { 
            'Content-Type': 'application/json'
            // Authorization 헤더 제거 - token은 body에 포함
        },
    };
    
    const reservationStart = Date.now();
    const reservationResponse = http.post(`${BASE_URL}/api/v1/reservations`, reservationPayload, reservationParams);
    const reservationDuration = Date.now() - reservationStart;
    
    const reservationSuccess = check(reservationResponse, {
        'reservation event status is 201': (r) => r.status === 201,
        'reservation event has reservationId': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.reservationId !== undefined;
            } catch (e) {
                return false;
            }
        },
        'reservation event response time < 5s': () => reservationDuration < 5000,
    });
    
    reservationEventRate.add(reservationSuccess);
    reservationResponseTime.add(reservationDuration);
    
    if (!reservationSuccess || reservationResponse.status !== 201) {
        console.log(`예약 실패: userId=${userId}, concertId=${concertId}, seatId=${seatInfo.seatId}, status=${reservationResponse.status}, 응답시간=${reservationDuration}ms`);
        
        // 에러 내용 로깅 (디버깅용)
        if (reservationResponse.body) {
            try {
                const errorBody = JSON.parse(reservationResponse.body);
                console.log(`예약 에러 메시지: ${errorBody.message || 'Unknown error'}`);
            } catch (e) {
                console.log(`예약 에러 응답: ${reservationResponse.body.substring(0, 200)}`);
            }
        }
        return;
    }
    
    let reservationId;
    try {
        const responseBody = JSON.parse(reservationResponse.body);
        const reservationData = responseBody.data;
        reservationId = reservationData.reservationId;
        console.log(`예약 성공: userId=${userId}, reservationId=${reservationId}, 응답시간=${reservationDuration}ms`);
    } catch (e) {
        console.log(`예약 데이터 파싱 실패: userId=${userId}`);
        return;
    }
    
    sleep(1);
    
    // 3단계: 결제 처리 (결제 이벤트 발생)
    const paymentPayload = JSON.stringify({
        userId: userId,
        reservationId: reservationId,
        seatId: seatInfo.seatId,
        amount: amount,
        token: token
    });
    
    const paymentParams = {
        headers: { 
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
    };
    
    const paymentStart = Date.now();
    const paymentResponse = http.post(`${BASE_URL}/api/v1/payments`, paymentPayload, paymentParams);
    const paymentDuration = Date.now() - paymentStart;
    
    const paymentSuccess = check(paymentResponse, {
        'payment event status is 201': (r) => r.status === 201,
        'payment event has paymentId': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.paymentId !== undefined;
            } catch (e) {
                return false;
            }
        },
        'payment event response time < 7s': () => paymentDuration < 7000,
    });
    
    paymentEventRate.add(paymentSuccess);
    paymentResponseTime.add(paymentDuration);
    
    if (paymentSuccess) {
        try {
            const responseBody = JSON.parse(paymentResponse.body);
            const paymentData = responseBody.data;
            console.log(`결제 성공: userId=${userId}, paymentId=${paymentData.paymentId}, amount=${amount}, 응답시간=${paymentDuration}ms`);
        } catch (e) {
            console.log(`결제 데이터 파싱 실패: userId=${userId}, 응답시간=${paymentDuration}ms`);
        }
    } else {
        console.log(`결제 실패: userId=${userId}, reservationId=${reservationId}, 응답시간=${paymentDuration}ms`);
    }
    
    sleep(1);
}

// 3. 혼합 이벤트 부하 테스트
export function mixedEventsTest() {
    const scenario = Math.random();
    
    if (scenario < 0.4) {
        // 40% 확률로 토큰 이벤트만 테스트
        tokenEventTest();
    } else if (scenario < 0.8) {
        // 40% 확률로 예약+결제 이벤트 테스트
        reservationPaymentEventTest();
    } else {
        // 20% 확률로 연속 처리 테스트 (토큰 → 예약 → 결제)
        const userId = generateUserId();
        console.log(`연속 처리 테스트 시작: userId=${userId}`);
        
        // 연속으로 여러 작업 수행
        for (let i = 0; i < 2; i++) {
            tokenEventTest();
            sleep(0.5);
        }
        
        sleep(1);
        reservationPaymentEventTest();
    }
}

// 테스트 시작 전 환경 확인
export function setup() {
    console.log('=== 카프카 이벤트 성능 테스트 시작 ===');
    console.log(`베이스 URL: ${BASE_URL}`);
    console.log('카프카 토픽:');
    console.log('- waiting-token (토큰 대기열)');
    console.log('- reservation-events (예약 이벤트)');
    console.log('- payment-events (결제 이벤트)');
    console.log('=====================================');
    
    // 서버 상태 확인
    const healthCheck = http.get(`${BASE_URL}/actuator/health`);
    if (healthCheck.status !== 200) {
        console.log('⚠️  서버 상태 확인 실패. 서버가 실행 중인지 확인하세요.');
    } else {
        console.log('✅ 서버 상태 확인 완료');
    }
}

// 테스트 종료 후 요약
export function teardown() {
    console.log('=== 카프카 이벤트 성능 테스트 완료 ===');
    console.log('결과 요약:');
    console.log('- 토큰 이벤트: 발행 속도와 카프카 처리 성능 측정');
    console.log('- 예약 이벤트: 예약 생성 시 이벤트 발생 테스트');
    console.log('- 결제 이벤트: 결제 완료/실패 시 이벤트 발생 테스트');
    console.log('상세 결과는 위의 메트릭을 참조하세요.');
    console.log('=====================================');
}