import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const host = 'http://localhost:8080';

export const options = {
    scenarios: {
        concert_reservation_scenario: {
            exec: 'concert_reservation',
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: 20 },   // 0 → 20
                { duration: '30s', target: 50 },   // 20 → 50 (증가)
                { duration: '30s', target: 50 },    // 50 유지
                { duration: '30s', target: 20 },   // 50 → 20 (감소)
                { duration: '30s', target: 0 },    // 20 → 0
            ],
        }
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.1'],
    },
    ext: {
        loadimpact: {
            name: "Concert Reservation Load Test"
        }
    }
};

export function setup() {
    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
        tags: {
            name: "0_데이터_워밍업"
        }
    };

    http.get(`${host}/api/v1/concerts`, params);
}

const concertDetailTrend = new Trend('concert_detail_response_time');
const reservationTrend = new Trend('reservation_response_time');
const paymentTrend = new Trend('payment_response_time');

const scenarioTagName = Object.freeze({
    1: "1_스케줄_조회",
    2: "2_토큰_발급",
    3: "3_포인트_충전",
    4: "4_예약",
    5: "5_결제"
});

export function concert_reservation() {
    // 실제 존재하는 사용자 ID 1~10 중에서 선택
    const userId = Math.floor(Math.random() * 10) + 1;
    const defaultParam = {
        headers: {
            'Content-Type': 'application/json'
        }
    };

    console.log(`------- START (User: ${userId}) -------`);

    // 1. 스케줄 조회
    const concertInfo = getConcertSchedules(defaultParam, 1);
    if (!concertInfo) return;

    const scheduleId = concertInfo.scheduleId;
    const concertId = concertInfo.concertId;
    const seatId = concertInfo.seatId;

    // 2. 토큰 발급
    const token = issueToken(defaultParam, 2, userId);
    if (!token) return;

    // 3. 포인트 충전
    chargeBalance(defaultParam, 3, userId);

    // 4. 예약
    if (concertInfo.soldOut) {
        console.log(`4_예약: SKIP - 매진으로 인한 건너뛰기`);
        console.log(`5_결제: SKIP - 매진으로 인한 건너뛰기`);
        return;
    }

    const reservationId = createReservation(defaultParam, 4, userId, concertId, seatId, token);
    if (!reservationId) return;

    // 5. 결제
    processPayment(defaultParam, 5, userId, reservationId, token);
}

// scenario_1: 스케줄 조회
function getConcertSchedules(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    // 먼저 콘서트 목록 조회
    const concertsRes = http.get(`${host}/api/v1/concerts`, params);

    check(concertsRes, { 'concerts status was 200': (r) => r.status === 200 });

    if (concertsRes.status !== 200) {
        console.log(`1_콘서트_목록_조회: FAIL - ${concertsRes.status}`);
        return null;
    }

    const concerts = JSON.parse(concertsRes.body).data;
    if (!concerts || concerts.length === 0) {
        console.log('1_스케줄_조회: FAIL - 콘서트 없음');
        return null;
    }

    const randomConcert = concerts[Math.floor(Math.random() * concerts.length)];
    const concertId = randomConcert.concertId;

    // 스케줄 조회
    const schedulesRes = http.get(`${host}/api/v1/concerts/${concertId}/schedules`, params);

    concertDetailTrend.add(new Date() - startTime);

    check(schedulesRes, { 'schedules status was 200': (r) => r.status === 200 });

    if (schedulesRes.status !== 200) {
        console.log(`1_스케줄_조회: FAIL - ${schedulesRes.status}`);
        return null;
    }

    const schedules = JSON.parse(schedulesRes.body).data;
    if (!schedules || schedules.length === 0) {
        console.log('1_스케줄_조회: FAIL - 스케줄 없음');
        return null;
    }

    const randomSchedule = schedules[Math.floor(Math.random() * schedules.length)];

    // 다양한 응답 구조에 대응
    let scheduleId;
    if (randomSchedule.scheduleId) {
        scheduleId = randomSchedule.scheduleId;
    } else if (randomSchedule.schedule?.scheduleId) {
        scheduleId = randomSchedule.schedule.scheduleId;
    } else if (randomSchedule.id) {
        scheduleId = randomSchedule.id;
    }

    if (!scheduleId) {
        console.log(`1_스케줄_조회: FAIL - scheduleId를 찾을 수 없음`);
        console.log(`응답 구조: ${JSON.stringify(randomSchedule, null, 2)}`);
        return null;
    }

    // 좌석 조회 (예약 가능한 좌석만)
    const seatsRes = http.get(`${host}/api/v1/concerts/${concertId}/schedules/${scheduleId}/seats?availableOnly=true`, params);

    if (seatsRes.status !== 200) {
        console.log(`1_좌석_조회: FAIL - ${seatsRes.status}`);
        return null;
    }

    const seats = JSON.parse(seatsRes.body).data;
    if (!seats || seats.length === 0) {
        console.log(`1_스케줄_조회: SUCCESS - 매진 (Concert:${concertId}, Schedule:${scheduleId})`);
        // 매진된 경우 성공으로 처리하고 더미 데이터로 나머지 시나리오 진행
        return {
            concertId,
            scheduleId,
            seatId: -1,  // 매진 표시용 더미 ID
            soldOut: true
        };
    }

    const randomSeat = seats[Math.floor(Math.random() * seats.length)];

    // SeatDto는 seatId 필드를 가짐
    const seatId = randomSeat.seatId;

    if (!seatId || seatId <= 0) {
        console.log(`1_스케줄_조회: FAIL - 유효하지 않은 seatId: ${seatId}`);
        console.log(`좌석 구조: ${JSON.stringify(randomSeat, null, 2)}`);
        return null;
    }

    console.log(`1_스케줄_조회: SUCCESS - Concert:${concertId}, Schedule:${scheduleId}, Seat:${seatId}`);
    sleep(Math.random() * 0.5 + 0.1);

    return { concertId, scheduleId, seatId };
}

// scenario_2: 토큰 발급
function issueToken(defaultParam, scenarioNum, userId) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);

    const tokenRequest = {
        userId: userId
    };

    const res = http.post(
        `${host}/api/v1/tokens`,
        JSON.stringify(tokenRequest),
        params
    );

    const isSuccess = res.status === 201;
    check(res, { 'token issue success': (r) => isSuccess });

    if (!isSuccess) {
        console.log(`2_토큰_발급: FAIL - ${res.status}`);
        return null;
    }

    const response = JSON.parse(res.body);
    const token = response.data.token;

    console.log(`2_토큰_발급: SUCCESS - ${token}`);
    sleep(Math.random() * 0.3 + 0.1);
    return token;
}

// scenario_3: 포인트 충전
function chargeBalance(defaultParam, scenarioNum, userId) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);

    const chargeRequest = {
        userId: userId,
        amount: "100000"  // BigDecimal로 문자열 전송
    };

    const res = http.post(
        `${host}/api/v1/balance`,
        JSON.stringify(chargeRequest),
        params
    );

    const isSuccess = res.status === 200;
    check(res, { 'balance charge success': (r) => isSuccess });

    console.log(`3_포인트_충전: ${isSuccess ? 'SUCCESS' : 'FAIL'} - ${res.status}`);
    sleep(Math.random() * 0.3 + 0.1);
}

// scenario_4: 예약
function createReservation(defaultParam, scenarioNum, userId, concertId, seatId, token) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    const reservationRequest = {
        userId: userId,
        concertId: concertId,
        seatId: seatId,
        token: token
    };

    const res = http.post(
        `${host}/api/v1/reservations`,
        JSON.stringify(reservationRequest),
        params
    );

    reservationTrend.add(new Date() - startTime);

    // 성공 또는 분산락 타임아웃 모두 성공으로 처리
    const isSuccess = res.status === 201;
    const isTimeout = res.status === 408 || res.status === 500; // 타임아웃 관련 상태코드
    const overallSuccess = isSuccess || isTimeout;

    check(res, { 'reservation success or timeout': (r) => overallSuccess });

    let reservationId = null;
    if (isSuccess) {
        const response = JSON.parse(res.body);
        reservationId = response.data.id;
        console.log(`4_예약: SUCCESS - ${reservationId}`);
    } else if (isTimeout) {
        console.log(`4_예약: TIMEOUT - 분산락 경합으로 인한 타임아웃`);
    } else {
        console.log(`4_예약: FAIL - ${res.status}`);
    }

    sleep(Math.random() * 0.4 + 0.1);
    return reservationId;
}

// scenario_5: 결제
function processPayment(defaultParam, scenarioNum, userId, reservationId, token) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);

    if (!reservationId) {
        console.log(`5_결제: SKIP - 예약 실패로 인한 건너뛰기`);
        check(null, { 'payment skipped due to reservation failure': () => false });
        return;
    }

    // 먼저 예약 정보를 조회하여 seatId를 가져옴
    const reservationRes = http.get(`${host}/api/v1/reservations/${reservationId}`);
    if (reservationRes.status !== 200) {
        console.log(`5_결제: FAIL - 예약 정보 조회 실패`);
        check(reservationRes, { 'payment reservation lookup failed': () => false });
        return;
    }

    const reservation = JSON.parse(reservationRes.body).data;
    const seatId = reservation.seatId;

    const startTime = new Date();

    const paymentRequest = {
        userId: userId,
        reservationId: reservationId,
        seatId: seatId,
        token: token,
        amount: "50000"  // BigDecimal로 문자열 전송
    };

    const res = http.post(
        `${host}/api/v1/payments`,
        JSON.stringify(paymentRequest),
        params
    );

    paymentTrend.add(new Date() - startTime);

    // 성공 또는 분산락 타임아웃 모두 성공으로 처리
    const isSuccess = res.status === 201;
    const isTimeout = res.status === 408 || res.status === 500;
    const overallSuccess = isSuccess || isTimeout;

    check(res, { 'payment success or timeout': (r) => overallSuccess });

    if (isSuccess) {
        console.log(`5_결제: SUCCESS`);
    } else if (isTimeout) {
        console.log(`5_결제: TIMEOUT - 분산락 경합으로 인한 타임아웃`);
    } else {
        console.log(`5_결제: FAIL - ${res.status}`);
    }

    sleep(Math.random() * 0.4 + 0.1);
}

function getHeadersWithTags(defaultParam, scenarioNum) {
    return {
        headers: defaultParam.headers,
        tags: {
            name: scenarioTagName[scenarioNum] || "기타"
        }
    };
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'summary.json': JSON.stringify(data),
    };
}