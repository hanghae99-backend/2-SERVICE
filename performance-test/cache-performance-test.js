import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const host = 'http://localhost:8080';

export const options = {
    scenarios: {
        cache_performance_scenario: {
            exec: 'cache_performance',
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: 10 },   // 워밍업
                { duration: '60s', target: 50 },   // 캐시 테스트 (첫 번째 라운드)
                { duration: '30s', target: 50 },   // 캐시 히트 테스트 (두 번째 라운드)  
                { duration: '30s', target: 0 },    // 종료
            ],
        }
    },
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.05'],
    },
    ext: {
        loadimpact: {
            name: "Cache Performance Test"
        }
    }
};

export function setup() {
    console.log('=== 캐시 성능 테스트 시작 ===');
    const params = {
        headers: {
            'Content-Type': 'application/json',
        }
    };

    // 캐시 워밍업
    console.log('캐시 워밍업 중...');
    http.get(`${host}/api/v1/concerts`, params);
    http.get(`${host}/api/v1/concerts/popular?limit=10`, params);
    http.get(`${host}/api/v1/concerts/trending?limit=5`, params);
    http.get(`${host}/api/v1/concerts/sellout-ranking?limit=10`, params);
    
    sleep(2); // 캐시 적용 대기
    console.log('캐시 워밍업 완료');
}

// 캐시 관련 API 응답 시간 측정용 Trends
const availableConcertsTrend = new Trend('available_concerts_response_time');
const popularConcertsTrend = new Trend('popular_concerts_response_time');
const trendingConcertsTrend = new Trend('trending_concerts_response_time');
const selloutRankingTrend = new Trend('sellout_ranking_response_time');
const concertDetailTrend = new Trend('concert_detail_response_time');
const concertSchedulesTrend = new Trend('concert_schedules_response_time');

const scenarioTagName = Object.freeze({
    1: "1_예약가능콘서트조회_캐시",
    2: "2_인기콘서트조회_캐시",
    3: "3_트렌딩콘서트조회_캐시",
    4: "4_매진랭킹조회_캐시",
    5: "5_콘서트상세조회_캐시",
    6: "6_콘서트스케줄조회_캐시"
});

export function cache_performance() {
    const defaultParam = {
        headers: {
            'Content-Type': 'application/json'
        }
    };

    const userId = Math.floor(Math.random() * 10) + 1;
    console.log(`------- 캐시 테스트 시작 (User: ${userId}) -------`);

    // 1. 예약 가능한 콘서트 목록 조회 (캐시 적용)
    testAvailableConcerts(defaultParam, 1);
    
    // 2. 인기 콘서트 조회 (캐시 적용)
    testPopularConcerts(defaultParam, 2);
    
    // 3. 트렌딩 콘서트 조회 (캐시 적용)
    testTrendingConcerts(defaultParam, 3);
    
    // 4. 매진 랭킹 조회 (캐시 적용)
    testSelloutRanking(defaultParam, 4);
    
    // 5. 콘서트 상세 조회 (캐시 적용)
    testConcertDetail(defaultParam, 5);
    
    // 6. 콘서트 스케줄 조회 (캐시 적용)
    testConcertSchedules(defaultParam, 6);

    sleep(0.1);
}

// 1. 예약 가능한 콘서트 목록 조회 (@Cacheable)
function testAvailableConcerts(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    const res = http.get(`${host}/api/v1/concerts`, params);
    
    availableConcertsTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '예약가능콘서트 조회 성공': (r) => r.status === 200,
        '예약가능콘서트 응답시간 < 500ms': (r) => r.timings.duration < 500
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`1_예약가능콘서트조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`1_예약가능콘서트조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 2. 인기 콘서트 조회 (@Cacheable)
function testPopularConcerts(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    const limit = Math.floor(Math.random() * 20) + 5; // 5-25 사이
    const res = http.get(`${host}/api/v1/concerts/popular?limit=${limit}`, params);
    
    popularConcertsTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '인기콘서트 조회 성공': (r) => r.status === 200,
        '인기콘서트 응답시간 < 300ms': (r) => r.timings.duration < 300
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`2_인기콘서트조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`2_인기콘서트조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 3. 트렌딩 콘서트 조회 (@Cacheable)
function testTrendingConcerts(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    const limit = Math.floor(Math.random() * 15) + 3; // 3-18 사이
    const res = http.get(`${host}/api/v1/concerts/trending?limit=${limit}`, params);
    
    trendingConcertsTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '트렌딩콘서트 조회 성공': (r) => r.status === 200,
        '트렌딩콘서트 응답시간 < 400ms': (r) => r.timings.duration < 400
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`3_트렌딩콘서트조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`3_트렌딩콘서트조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 4. 매진 랭킹 조회 (@Cacheable)
function testSelloutRanking(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    const limit = Math.floor(Math.random() * 30) + 10; // 10-40 사이
    const res = http.get(`${host}/api/v1/concerts/sellout-ranking?limit=${limit}`, params);
    
    selloutRankingTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '매진랭킹 조회 성공': (r) => r.status === 200,
        '매진랭킹 응답시간 < 600ms': (r) => r.timings.duration < 600
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`4_매진랭킹조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`4_매진랭킹조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 5. 콘서트 상세 조회 (@Cacheable)
function testConcertDetail(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    // 실제 존재하는 콘서트 ID 1-5 중 선택
    const concertId = Math.floor(Math.random() * 5) + 1;
    const res = http.get(`${host}/api/v1/concerts/${concertId}`, params);
    
    concertDetailTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '콘서트상세 조회 성공': (r) => r.status === 200,
        '콘서트상세 응답시간 < 300ms': (r) => r.timings.duration < 300
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`5_콘서트상세조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ID:${data?.concertId || 'N/A'}`);
    } else {
        console.log(`5_콘서트상세조회: FAIL - ${res.status}`);
    }
    
    sleep(0.1);
}

// 6. 콘서트 스케줄 조회 (@Cacheable)
function testConcertSchedules(defaultParam, scenarioNum) {
    const params = getHeadersWithTags(defaultParam, scenarioNum);
    const startTime = new Date();

    // 실제 존재하는 콘서트 ID 1-5 중 선택
    const concertId = Math.floor(Math.random() * 5) + 1;
    const res = http.get(`${host}/api/v1/concerts/${concertId}/schedules`, params);
    
    concertSchedulesTrend.add(new Date() - startTime);
    
    const isSuccess = check(res, { 
        '콘서트스케줄 조회 성공': (r) => r.status === 200,
        '콘서트스케줄 응답시간 < 400ms': (r) => r.timings.duration < 400
    });
    
    if (isSuccess) {
        const data = JSON.parse(res.body).data;
        console.log(`6_콘서트스케줄조회: SUCCESS (${res.timings.duration.toFixed(2)}ms) - ${data?.length || 0}건`);
    } else {
        console.log(`6_콘서트스케줄조회: FAIL - ${res.status}`);
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