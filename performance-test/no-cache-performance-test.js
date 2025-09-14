import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const host = 'http://localhost:8080';

export const options = {
    scenarios: {
        no_cache_performance_scenario: {
            exec: 'no_cache_performance',
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: 10 },   // 워밍업
                { duration: '60s', target: 50 },   // 메인 테스트
                { duration: '30s', target: 50 },   // 지속 테스트
                { duration: '30s', target: 0 },    // 종료
            ],
        }
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'], // 캐시 없을 때는 더 느릴 수 있음
        http_req_failed: ['rate<0.05'],
    },
    ext: {
        loadimpact: {
            name: "No Cache Performance Test"
        }
    }
};

export function setup() {
    console.log('=== 캐시 미사용 성능 테스트 시작 ===');
    console.log('주의: 이 테스트는 캐시를 우회하여 항상 DB에서 조회합니다.');
    
    const params = {
        headers: {
            'Content-Type': 'application/json',
        }
    };
    
    // DB 워밍업 (커넥션 풀 준비)
    console.log('DB 연결 워밍업 중...');
    http.get(`${host}/api/v1/concerts/1`, params); // 단일 조회로 워밍업
    sleep(1);
    console.log('DB 워밍업 완료');
}

// 캐시 미사용 API 응답 시간 측정용 Trends
const noCacheAvailableTrend = new Trend('no_cache_available_concerts_response_time');
const noCachePopularTrend = new Trend('no_cache_popular_concerts_response_time');
const noCacheTrendingTrend = new Trend('no_cache_trending_concerts_response_time');
const noCacheSelloutTrend = new Trend('no_cache_sellout_ranking_response_time');
const noCacheDetailTrend = new Trend('no_cache_concert_detail_response_time');
const noCacheSchedulesTrend = new Trend('no_cache_concert_schedules_response_time');

const scenarioTagName = Object.freeze({
    1: "1_예약가능콘서트조회_노캐시",
    2: "2_인기콘서트조회_노캐시",
    3: "3_트렌딩콘서트조회_노캐시",
    4: "4_매진랭킹조회_노캐시",
    5: "5_콘서트상세조회_노캐시",
    6: "6_콘서트스케줄조회_노캐시"
});

export function no_cache_performance() {
    const defaultParam = {
        headers: {
            'Content-Type': 'application/json',
            'Cache-Control': 'no-cache, no-store, must-revalidate',  // 캐시 무효화
            'Pragma': 'no-cache',                                    // HTTP/1.0 캐시 무효화
            'Expires': '0'                                           // 만료 즉시
        }
    };

    const userId = Math.floor(Math.random() * 10) + 1;
    console.log(`------- 노캐시 테스트 시작 (User: ${userId}) -------`);

    // 1. 예약 가능한 콘서트 목록 조회 (캐시 우회)
    testNoCacheAvailableConcerts(defaultParam, 1);
    
    // 2. 인기 콘서트 조회 (캐시 우회) - 매번 다른 파라미터로 캐시 우회
    testNoCachePopularConcerts(defaultParam, 2);
    
    // 3. 트렌딩 콘서트 조회 (캐시 우회) - 매번 다른 파라미터로 캐시 우회
    testNoCacheTrendingConcerts(defaultParam, 3);
    
    // 4. 매진 랭킹 조회 (캐시 우회) - 매번 다른 파라미터로 캐시 우회
    testNoCacheSelloutRanking(defaultParam, 4);
    
    // 5. 콘서트 상세 조회 (캐시 우회) - 다양한 ID로 캐시 우회
    testNoCacheConcertDetail(defaultParam, 5);
    
    // 6. 콘서트 스케줄 조회 (캐시 우회) - 다양한 ID로 캐시 우회
    testNoCacheConcertSchedules(defaultParam, 6);

    sleep(0.1);
}

// 1. 예약 가능한 콘서트 목록 조회 (캐시 우회)
function testNoCacheAvailableConcerts(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    // 매번 다른 날짜 범위로 요청하여 캐시 우회
    const today = new Date();
    const startDate = new Date(today.getTime() + Math.random() * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    const endDate = new Date(today.getTime() + (Math.random() + 30) * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    
    const res = http.get(`${host}/api/v1/concerts?startDate=${startDate}&endDate=${endDate}`, params);
    
    noCacheAvailableTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '노캐시_예약가능콘서트 조회 성공': (r) => r.status === 200,
        '노캐시_예약가능콘서트 응답시간 < 1000ms': (r) => r.timings.duration < 1000
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`1_노캐시_예약가능콘서트조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`1_노캐시_예약가능콘서트조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 2. 인기 콘서트 조회 (캐시 우회)
function testNoCachePopularConcerts(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    // 매번 다른 limit으로 캐시 키 변경하여 우회
    const limit = Math.floor(Math.random() * 20) + 5; // 5-25 사이
    const timestamp = new Date().getTime(); // 캐시 무효화용
    const res = http.get(`${host}/api/v1/concerts/popular?limit=${limit}&_t=${timestamp}`, params);
    
    noCachePopularTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '노캐시_인기콘서트 조회 성공': (r) => r.status === 200,
        '노캐시_인기콘서트 응답시간 < 800ms': (r) => r.timings.duration < 800
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`2_노캐시_인기콘서트조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`2_노캐시_인기콘서트조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 3. 트렌딩 콘서트 조회 (캐시 우회)
function testNoCacheTrendingConcerts(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    const limit = Math.floor(Math.random() * 15) + 3; // 3-18 사이
    const timestamp = new Date().getTime();
    const res = http.get(`${host}/api/v1/concerts/trending?limit=${limit}&_t=${timestamp}`, params);
    
    noCacheTrendingTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '노캐시_트렌딩콘서트 조회 성공': (r) => r.status === 200,
        '노캐시_트렌딩콘서트 응답시간 < 1000ms': (r) => r.timings.duration < 1000
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`3_노캐시_트렌딩콘서트조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`3_노캐시_트렌딩콘서트조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 4. 매진 랭킹 조회 (캐시 우회)
function testNoCacheSelloutRanking(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    const limit = Math.floor(Math.random() * 30) + 10; // 10-40 사이
    const timestamp = new Date().getTime();
    const res = http.get(`${host}/api/v1/concerts/sellout-ranking?limit=${limit}&_t=${timestamp}`, params);
    
    noCacheSelloutTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '노캐시_매진랭킹 조회 성공': (r) => r.status === 200,
        '노캐시_매진랭킹 응답시간 < 1200ms': (r) => r.timings.duration < 1200
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`4_노캐시_매진랭킹조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`4_노캐시_매진랭킹조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 5. 콘서트 상세 조회 (캐시 우회)
function testNoCacheConcertDetail(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    // 다양한 콘서트 ID로 캐시 우회 (1-10 범위)
    const concertId = Math.floor(Math.random() * 10) + 1;
    const timestamp = new Date().getTime();
    const res = http.get(`${host}/api/v1/concerts/${concertId}?_t=${timestamp}`, params);
    
    noCacheDetailTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '노캐시_콘서트상세 조회 성공': (r) => r.status === 200,
        '노캐시_콘서트상세 응답시간 < 500ms': (r) => r.timings.duration < 500
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`5_노캐시_콘서트상세조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ID:${data?.concertId || 'N/A'}`);
    } else {
        console.log(`5_노캐시_콘서트상세조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 6. 콘서트 스케줄 조회 (캐시 우회)
function testNoCacheConcertSchedules(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    // 다양한 콘서트 ID로 캐시 우회
    const concertId = Math.floor(Math.random() * 10) + 1;
    const timestamp = new Date().getTime();
    const res = http.get(`${host}/api/v1/concerts/${concertId}/schedules?_t=${timestamp}`, params);
    
    noCacheSchedulesTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '노캐시_콘서트스케줄 조회 성공': (r) => r.status === 200,
        '노캐시_콘서트스케줄 응답시간 < 700ms': (r) => r.timings.duration < 700
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`6_노캐시_콘서트스케줄조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`6_노캐시_콘서트스케줄조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
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