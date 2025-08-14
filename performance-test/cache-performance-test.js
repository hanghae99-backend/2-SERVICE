import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// 커스텀 메트릭
const cacheHitRate = new Rate('cache_hit_rate');
const cacheMissRate = new Rate('cache_miss_rate');
const cacheHitResponseTime = new Trend('cache_hit_response_time');
const cacheMissResponseTime = new Trend('cache_miss_response_time');
const totalCacheRequests = new Counter('total_cache_requests');

export let options = {
  scenarios: {
    // 시나리오 1: 캐시 워밍업
    cache_warmup: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      startTime: '0s',
      tags: { test_type: 'warmup' },
    },
    
    // 시나리오 2: 캐시 성능 테스트 (같은 데이터 반복 요청)
    cache_performance: {
      executor: 'ramping-vus',
      startVUs: 5,
      stages: [
        { duration: '30s', target: 20 },   // 워밍업 완료 후 시작
        { duration: '1m', target: 50 },    // 점진적 증가
        { duration: '1m', target: 100 },   // 최대 부하
        { duration: '30s', target: 50 },   // 감소
        { duration: '30s', target: 0 },    // 종료
      ],
      startTime: '10s', // 워밍업 후 시작
      tags: { test_type: 'performance' },
    },
    
    // 시나리오 3: 캐시 미스 테스트 (다양한 데이터 요청)
    cache_miss_test: {
      executor: 'constant-vus',
      vus: 10,
      duration: '2m',
      startTime: '60s', // 캐시 성능 테스트와 병행
      tags: { test_type: 'cache_miss' },
    }
  },
  
  thresholds: {
    'http_req_duration': ['p(95)<2000'],           // 전체 응답시간
    'cache_hit_response_time': ['p(95)<500'],      // 캐시 히트 응답시간
    'cache_miss_response_time': ['p(95)<2000'],    // 캐시 미스 응답시간
    'cache_hit_rate': ['rate>0.7'],               // 캐시 적중률 70% 이상
    'http_req_failed': ['rate<0.05'],             // 실패율 5% 이하
  },
};

const BASE_URL = 'http://localhost:8080';

// 테스트할 콘서트 ID들 (실제 DB에 있는 ID로 변경 필요)
const CONCERT_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const SCHEDULE_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

export default function() {
  const scenario = __VU <= 1 ? 'warmup' : 
                   (__ITER % 3 === 0 ? 'cache_miss' : 'performance');
  
  switch (scenario) {
    case 'warmup':
      warmupCache();
      break;
    case 'performance':
      testCachePerformance();
      break;
    case 'cache_miss':
      testCacheMiss();
      break;
  }
}

// 캐시 워밍업: 모든 주요 데이터를 미리 로드
function warmupCache() {
  console.log('🔥 캐시 워밍업 시작...');
  
  // 1. 인기 콘서트 캐시 워밍업
  let popularResponse = http.get(`${BASE_URL}/api/v1/v1/concerts/popular?limit=10`, {
    tags: { operation: 'popular_concerts', phase: 'warmup' }
  });
  
  check(popularResponse, {
    'popular concerts warmup success': (r) => r.status === 200,
  });
  
  // 2. 사용 가능한 콘서트 캐시 워밍업
  let availableResponse = http.get(`${BASE_URL}/api/v1/v1/concerts`, {
    tags: { operation: 'available_concerts', phase: 'warmup' }
  });
  
  check(availableResponse, {
    'available concerts warmup success': (r) => r.status === 200,
  });
  
  // 3. 개별 콘서트 캐시 워밍업
  CONCERT_IDS.slice(0, 5).forEach(concertId => {
    let concertResponse = http.get(`${BASE_URL}/api/v1/v1/concerts/${concertId}`, {
      tags: { operation: 'concert_detail', phase: 'warmup' }
    });
    
    check(concertResponse, {
      [`concert ${concertId} warmup success`]: (r) => r.status === 200,
    });
  });
  
  // 4. 콘서트 상세 정보 캐시 워밍업
  SCHEDULE_IDS.slice(0, 3).forEach(scheduleId => {
    let detailResponse = http.get(`${BASE_URL}/api/v1/v1/concerts/1/schedules/${scheduleId}`, {
      tags: { operation: 'concert_schedule_detail', phase: 'warmup' }
    });
    
    check(detailResponse, {
      [`schedule ${scheduleId} detail warmup success`]: (r) => r.status === 200,
    });
  });
  
  console.log('✅ 캐시 워밍업 완료');
  sleep(2);
}

// 캐시 성능 테스트: 동일한 데이터를 반복 요청하여 캐시 히트율 측정
function testCachePerformance() {
  const testOperations = [
    'popular_concerts',
    'available_concerts', 
    'concert_detail',
    'trending_concerts'
  ];
  
  const operation = testOperations[Math.floor(Math.random() * testOperations.length)];
  
  switch (operation) {
    case 'popular_concerts':
      testPopularConcertsCache();
      break;
    case 'available_concerts':
      testAvailableConcertsCache();
      break;
    case 'concert_detail':
      testConcertDetailCache();
      break;
    case 'trending_concerts':
      testTrendingConcertsCache();
      break;
  }
  
  sleep(Math.random() * 0.5); // 0-500ms 대기
}

// 캐시 미스 테스트: 다양한 파라미터로 캐시 미스 유발
function testCacheMiss() {
  const operations = [
    'different_date_range',
    'random_concert_id',
    'random_schedule_id',
    'different_popular_limit'
  ];
  
  const operation = operations[Math.floor(Math.random() * operations.length)];
  
  switch (operation) {
    case 'different_date_range':
      testDifferentDateRange();
      break;
    case 'random_concert_id':
      testRandomConcertId();
      break;
    case 'random_schedule_id':
      testRandomScheduleId();
      break;
    case 'different_popular_limit':
      testDifferentPopularLimit();
      break;
  }
  
  sleep(Math.random() * 1); // 0-1초 대기
}

function testPopularConcertsCache() {
  let startTime = Date.now();
  
  let response = http.get(`${BASE_URL}/api/v1/v1/concerts/popular?limit=10`, {
    tags: { operation: 'popular_concerts', cache_type: 'hit_expected' }
  });
  
  let responseTime = Date.now() - startTime;
  let isCacheHit = responseTime < 100; // 100ms 이하면 캐시 히트로 간주
  
  totalCacheRequests.add(1);
  
  if (isCacheHit) {
    cacheHitRate.add(1);
    cacheHitResponseTime.add(responseTime);
  } else {
    cacheMissRate.add(1);
    cacheMissResponseTime.add(responseTime);
  }
  
  check(response, {
    'popular concerts status 200': (r) => r.status === 200,
    'popular concerts response time OK': (r) => r.timings.duration < 1000,
    'popular concerts cache hit': () => isCacheHit,
  });
}

function testAvailableConcertsCache() {
  let startTime = Date.now();
  
  let response = http.get(`${BASE_URL}/api/v1/v1/concerts`, {
    tags: { operation: 'available_concerts', cache_type: 'hit_expected' }
  });
  
  let responseTime = Date.now() - startTime;
  let isCacheHit = responseTime < 150;
  
  totalCacheRequests.add(1);
  
  if (isCacheHit) {
    cacheHitRate.add(1);
    cacheHitResponseTime.add(responseTime);
  } else {
    cacheMissRate.add(1);
    cacheMissResponseTime.add(responseTime);
  }
  
  check(response, {
    'available concerts status 200': (r) => r.status === 200,
    'available concerts cache hit': () => isCacheHit,
  });
}

function testConcertDetailCache() {
  const concertId = CONCERT_IDS[Math.floor(Math.random() * Math.min(5, CONCERT_IDS.length))];
  let startTime = Date.now();
  
  let response = http.get(`${BASE_URL}/api/v1/v1/concerts/${concertId}`, {
    tags: { operation: 'concert_detail', cache_type: 'hit_expected' }
  });
  
  let responseTime = Date.now() - startTime;
  let isCacheHit = responseTime < 80;
  
  totalCacheRequests.add(1);
  
  if (isCacheHit) {
    cacheHitRate.add(1);
    cacheHitResponseTime.add(responseTime);
  } else {
    cacheMissRate.add(1);
    cacheMissResponseTime.add(responseTime);
  }
  
  check(response, {
    'concert detail status 200': (r) => r.status === 200,
    'concert detail cache hit': () => isCacheHit,
  });
}

function testTrendingConcertsCache() {
  let startTime = Date.now();
  
  let response = http.get(`${BASE_URL}/api/v1/v1/concerts/trending?limit=5`, {
    tags: { operation: 'trending_concerts', cache_type: 'hit_expected' }
  });
  
  let responseTime = Date.now() - startTime;
  let isCacheHit = responseTime < 100;
  
  totalCacheRequests.add(1);
  
  if (isCacheHit) {
    cacheHitRate.add(1);
    cacheHitResponseTime.add(responseTime);
  } else {
    cacheMissRate.add(1);
    cacheMissResponseTime.add(responseTime);
  }
  
  check(response, {
    'trending concerts status 200': (r) => r.status === 200,
    'trending concerts cache hit': () => isCacheHit,
  });
}

// 캐시 미스 유발 함수들
function testDifferentDateRange() {
  const randomDays = Math.floor(Math.random() * 30) + 1;
  let response = http.get(`${BASE_URL}/api/v1/v1/concerts?days=${randomDays}`, {
    tags: { operation: 'available_concerts_different_params', cache_type: 'miss_expected' }
  });
  
  cacheMissRate.add(1);
  totalCacheRequests.add(1);
  
  check(response, {
    'different date range status 200': (r) => r.status === 200,
  });
}

function testRandomConcertId() {
  const randomId = Math.floor(Math.random() * 1000) + 100; // 존재하지 않을 ID
  let response = http.get(`${BASE_URL}/api/v1/v1/concerts/${randomId}`, {
    tags: { operation: 'concert_detail_random', cache_type: 'miss_expected' }
  });
  
  cacheMissRate.add(1);
  totalCacheRequests.add(1);
  
  // 404도 정상적인 응답으로 간주
  check(response, {
    'random concert id response': (r) => r.status === 200 || r.status === 404,
  });
}

function testRandomScheduleId() {
  const randomId = Math.floor(Math.random() * 1000) + 100;
  let response = http.get(`${BASE_URL}/api/v1/v1/concerts/1/schedules/${randomId}`, {
    tags: { operation: 'schedule_detail_random', cache_type: 'miss_expected' }
  });
  
  cacheMissRate.add(1);
  totalCacheRequests.add(1);
  
  check(response, {
    'random schedule id response': (r) => r.status === 200 || r.status === 404,
  });
}

function testDifferentPopularLimit() {
  const limits = [3, 15, 20, 25];
  const limit = limits[Math.floor(Math.random() * limits.length)];
  
  let response = http.get(`${BASE_URL}/api/v1/v1/concerts/popular?limit=${limit}`, {
    tags: { operation: 'popular_concerts_different_limit', cache_type: 'miss_expected' }
  });
  
  cacheMissRate.add(1);
  totalCacheRequests.add(1);
  
  check(response, {
    'different popular limit status 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  const totalRequests = data.metrics.total_cache_requests?.values.count || 0;
  const hitRate = data.metrics.cache_hit_rate?.values.rate || 0;
  const missRate = data.metrics.cache_miss_rate?.values.rate || 0;
  const avgHitTime = data.metrics.cache_hit_response_time?.values.avg || 0;
  const avgMissTime = data.metrics.cache_miss_response_time?.values.avg || 0;
  
  return {
    'cache-performance-summary.json': JSON.stringify(data, null, 2),
    stdout: `
═══════════════════════════════════════
🚀 캐시 성능 테스트 결과
═══════════════════════════════════════

📊 전체 통계:
  🔢 총 캐시 요청: ${totalRequests}
  📈 전체 HTTP 요청: ${data.metrics.http_reqs?.values.count || 0}
  ❌ 실패율: ${((data.metrics.http_req_failed?.values.rate || 0) * 100).toFixed(2)}%

🎯 캐시 성능:
  ✅ 캐시 적중률: ${(hitRate * 100).toFixed(2)}%
  ❌ 캐시 미스율: ${(missRate * 100).toFixed(2)}%
  
⏱️  응답 시간:
  🟢 캐시 히트 평균: ${avgHitTime.toFixed(2)}ms
  🔴 캐시 미스 평균: ${avgMissTime.toFixed(2)}ms
  📈 성능 향상: ${avgMissTime > 0 ? ((avgMissTime - avgHitTime) / avgMissTime * 100).toFixed(1) : 0}%

🎯 목표 달성도:
  ${hitRate > 0.7 ? '✅' : '❌'} 캐시 적중률 목표 (70% 이상): ${(hitRate * 100).toFixed(2)}%
  ${avgHitTime < 500 ? '✅' : '❌'} 캐시 히트 응답시간 목표 (500ms 이하): ${avgHitTime.toFixed(2)}ms
  ${data.metrics.http_req_failed?.values.rate < 0.05 ? '✅' : '❌'} 실패율 목표 (5% 이하)

💡 캐시 효율성 지수: ${avgMissTime > 0 ? (avgMissTime / avgHitTime).toFixed(1) : 'N/A'}x
   (캐시가 없을 때 대비 성능 향상 배수)

═══════════════════════════════════════
    `,
  };
}
