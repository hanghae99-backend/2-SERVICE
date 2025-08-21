import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 3가지 방식별 성능 메트릭
const schedulerResponseTime = new Trend('scheduler_response_time');
const cacheableResponseTime = new Trend('cacheable_response_time');
const directResponseTime = new Trend('direct_response_time');

const schedulerRequests = new Counter('scheduler_requests');
const cacheableRequests = new Counter('cacheable_requests');
const directRequests = new Counter('direct_requests');

export let options = {
  scenarios: {
    // 캐시 초기화 및 워밍업
    warmup: {
      executor: 'constant-vus',
      vus: 2,
      duration: '30s',
      tags: { test_type: 'warmup' },
    },
    
    // 스케줄러 방식 테스트
    scheduler_test: {
      executor: 'constant-vus',
      vus: 20,
      duration: '2m',
      startTime: '30s',
      tags: { test_type: 'scheduler' },
    },
    
    // @Cacheable 방식 테스트 (현재 기본 엔드포인트)
    cacheable_test: {
      executor: 'constant-vus',
      vus: 20,
      duration: '2m',
      startTime: '3m',
      tags: { test_type: 'cacheable' },
    },
    
    // 직접 조회 방식 테스트
    direct_test: {
      executor: 'constant-vus',
      vus: 20,
      duration: '2m',
      startTime: '5m30s',
      tags: { test_type: 'direct' },
    }
  },
  
  thresholds: {
    'scheduler_response_time': ['p(95)<200'],
    'cacheable_response_time': ['p(95)<200'],
    'direct_response_time': ['p(95)<500'],
    'http_req_failed': ['rate<0.05'],
  },
};

const BASE_URL = 'http://localhost:8080';

export default function() {
  const testType = __ENV.K6_SCENARIO || getTestType();
  
  switch (testType) {
    case 'warmup':
      warmupCaches();
      break;
    case 'scheduler_test':
      testSchedulerMethod();
      break;
    case 'cacheable_test':
      testCacheableMethod();
      break;
    case 'direct_test':
      testDirectMethod();
      break;
  }
}

function getTestType() {
  const elapsed = (__ITER * __VU) % 450; // 7분 30초 주기
  if (elapsed < 30) return 'warmup';
  if (elapsed < 150) return 'scheduler_test';
  if (elapsed < 270) return 'cacheable_test';
  return 'direct_test';
}

// 캐시 워밍업
function warmupCaches() {
  // 캐시 초기화
  http.post(`${BASE_URL}/api/test/cache/cache/clear`);
  sleep(1);
  
  // 각 방식별 캐시 워밍업
  http.post(`${BASE_URL}/api/test/cache/cache/warmup?limit=10`);
  
  // 기본 엔드포인트도 워밍업
  http.get(`${BASE_URL}/api/v1/concerts/popular?limit=10`);
  http.get(`${BASE_URL}/api/v1/concerts/popular?limit=5`);
  http.get(`${BASE_URL}/api/v1/concerts/trending?limit=5`);
  
  sleep(0.5);
}

// 스케줄러 방식 테스트
function testSchedulerMethod() {
  const operations = [
    () => testSchedulerPopular(),
    () => testSchedulerTrending(),
    () => testSchedulerConcertDetail(),
  ];
  
  const operation = operations[Math.floor(Math.random() * operations.length)];
  operation();
  
  sleep(0.1);
}

function testSchedulerPopular() {
  const limits = [5, 10]; // 스케줄러가 캐시하는 limit만 사용
  const limit = limits[Math.floor(Math.random() * limits.length)];
  
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/test/cache/popular/scheduler?limit=${limit}`, {
    tags: { method: 'scheduler', operation: 'popular' }
  });
  const responseTime = Date.now() - startTime;
  
  schedulerResponseTime.add(responseTime);
  schedulerRequests.add(1);
  
  check(response, {
    'scheduler popular status 200': (r) => r.status === 200,
    'scheduler popular has data': (r) => r.json('data.data') !== undefined,
    'scheduler popular fast': () => responseTime < 200,
  });
}

function testSchedulerTrending() {
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/test/cache/popular/scheduler?limit=5`, {
    tags: { method: 'scheduler', operation: 'trending' }
  });
  const responseTime = Date.now() - startTime;
  
  schedulerResponseTime.add(responseTime);
  schedulerRequests.add(1);
  
  check(response, {
    'scheduler trending status 200': (r) => r.status === 200,
    'scheduler trending fast': () => responseTime < 200,
  });
}

function testSchedulerConcertDetail() {
  const concertId = Math.floor(Math.random() * 10) + 1; // 1-10 (스케줄러가 캐시하는 범위)
  
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/v1/concerts/${concertId}`, {
    tags: { method: 'scheduler', operation: 'detail' }
  });
  const responseTime = Date.now() - startTime;
  
  schedulerResponseTime.add(responseTime);
  schedulerRequests.add(1);
  
  check(response, {
    'scheduler detail status 200': (r) => r.status === 200,
    'scheduler detail fast': () => responseTime < 200,
  });
}

// @Cacheable 방식 테스트 (현재 기본 엔드포인트)
function testCacheableMethod() {
  const operations = [
    () => testCacheablePopular(),
    () => testCacheableTrending(),
    () => testCacheableConcertDetail(),
    () => testCacheableAvailable(),
  ];
  
  const operation = operations[Math.floor(Math.random() * operations.length)];
  operation();
  
  sleep(0.1);
}

function testCacheablePopular() {
  const limits = [5, 10, 15]; // 다양한 limit으로 @Cacheable 테스트
  const limit = limits[Math.floor(Math.random() * limits.length)];
  
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/v1/concerts/popular?limit=${limit}`, {
    tags: { method: 'cacheable', operation: 'popular' }
  });
  const responseTime = Date.now() - startTime;
  
  cacheableResponseTime.add(responseTime);
  cacheableRequests.add(1);
  
  check(response, {
    'cacheable popular status 200': (r) => r.status === 200,
    'cacheable popular has data': (r) => r.json('data') !== undefined,
    'cacheable popular fast': () => responseTime < 200,
  });
}

function testCacheableTrending() {
  const limits = [3, 5, 7];
  const limit = limits[Math.floor(Math.random() * limits.length)];
  
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/v1/concerts/trending?limit=${limit}`, {
    tags: { method: 'cacheable', operation: 'trending' }
  });
  const responseTime = Date.now() - startTime;
  
  cacheableResponseTime.add(responseTime);
  cacheableRequests.add(1);
  
  check(response, {
    'cacheable trending status 200': (r) => r.status === 200,
    'cacheable trending fast': () => responseTime < 200,
  });
}

function testCacheableConcertDetail() {
  const concertId = Math.floor(Math.random() * 20) + 1; // 1-20
  
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/v1/concerts/${concertId}`, {
    tags: { method: 'cacheable', operation: 'detail' }
  });
  const responseTime = Date.now() - startTime;
  
  cacheableResponseTime.add(responseTime);
  cacheableRequests.add(1);
  
  check(response, {
    'cacheable detail status 200': (r) => r.status === 200,
    'cacheable detail fast': () => responseTime < 200,
  });
}

function testCacheableAvailable() {
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/v1/concerts`, {
    tags: { method: 'cacheable', operation: 'available' }
  });
  const responseTime = Date.now() - startTime;
  
  cacheableResponseTime.add(responseTime);
  cacheableRequests.add(1);
  
  check(response, {
    'cacheable available status 200': (r) => r.status === 200,
    'cacheable available fast': () => responseTime < 300,
  });
}

// 직접 조회 방식 테스트
function testDirectMethod() {
  const operations = [
    () => testDirectPopular(),
    () => testDirectTrending(), 
    () => testDirectConcertDetail(),
  ];
  
  const operation = operations[Math.floor(Math.random() * operations.length)];
  operation();
  
  sleep(0.1);
}

function testDirectPopular() {
  const limits = [7, 12, 18, 25]; // 캐시되지 않는 limit들
  const limit = limits[Math.floor(Math.random() * limits.length)];
  
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/test/cache/popular/direct?limit=${limit}`, {
    tags: { method: 'direct', operation: 'popular' }
  });
  const responseTime = Date.now() - startTime;
  
  directResponseTime.add(responseTime);
  directRequests.add(1);
  
  check(response, {
    'direct popular status 200': (r) => r.status === 200,
    'direct popular has data': (r) => r.json('data.data') !== undefined,
    'direct popular reasonable': () => responseTime < 500,
  });
}

function testDirectTrending() {
  const limits = [8, 12, 15];
  const limit = limits[Math.floor(Math.random() * limits.length)];
  
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/test/cache/popular/direct?limit=${limit}`, {
    tags: { method: 'direct', operation: 'trending' }
  });
  const responseTime = Date.now() - startTime;
  
  directResponseTime.add(responseTime);
  directRequests.add(1);
  
  check(response, {
    'direct trending status 200': (r) => r.status === 200,
    'direct trending reasonable': () => responseTime < 500,
  });
}

function testDirectConcertDetail() {
  const concertId = Math.floor(Math.random() * 50) + 21; // 21-70 (캐시되지 않는 범위)
  
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/v1/concerts/${concertId}`, {
    tags: { method: 'direct', operation: 'detail' }
  });
  const responseTime = Date.now() - startTime;
  
  directResponseTime.add(responseTime);
  directRequests.add(1);
  
  check(response, {
    'direct detail response': (r) => r.status === 200 || r.status === 404, // 404도 정상 (존재하지 않는 ID)
    'direct detail reasonable': () => responseTime < 500,
  });
}

export function handleSummary(data) {
  const schedulerAvg = data.metrics.scheduler_response_time?.values.avg || 0;
  const cacheableAvg = data.metrics.cacheable_response_time?.values.avg || 0;
  const directAvg = data.metrics.direct_response_time?.values.avg || 0;
  
  const scheduler95p = data.metrics.scheduler_response_time?.values['p(95)'] || 0;
  const cacheable95p = data.metrics.cacheable_response_time?.values['p(95)'] || 0;
  const direct95p = data.metrics.direct_response_time?.values['p(95)'] || 0;
  
  const schedulerCount = data.metrics.scheduler_requests?.values.count || 0;
  const cacheableCount = data.metrics.cacheable_requests?.values.count || 0;
  const directCount = data.metrics.direct_requests?.values.count || 0;
  
  const totalRequests = schedulerCount + cacheableCount + directCount;
  const testDuration = 120; // 각 테스트는 2분씩
  
  const schedulerTPS = schedulerCount / testDuration;
  const cacheableTPS = cacheableCount / testDuration;
  const directTPS = directCount / testDuration;
  
  // 승자 결정
  const winner = schedulerAvg <= cacheableAvg && schedulerAvg <= directAvg ? 'SCHEDULER' :
                 cacheableAvg <= schedulerAvg && cacheableAvg <= directAvg ? 'CACHEABLE' : 'DIRECT';
  
  const performanceGap = Math.abs(schedulerAvg - cacheableAvg);
  const significant = performanceGap > 10; // 10ms 이상 차이나면 의미있는 차이
  
  return {
    'cache-method-comparison.json': JSON.stringify(data, null, 2),
    stdout: `
═══════════════════════════════════════════════════════════
🔬 스케줄러 vs @Cacheable vs Direct 방식 성능 비교 결과
═══════════════════════════════════════════════════════════

📊 평균 응답 시간 비교:
  🤖 스케줄러 방식: ${schedulerAvg.toFixed(2)}ms
  ⚡ @Cacheable 방식: ${cacheableAvg.toFixed(2)}ms  
  🔄 Direct 방식: ${directAvg.toFixed(2)}ms
  
  🏆 가장 빠른 방식: ${winner}

📈 95% 응답 시간:
  🤖 스케줄러 95%: ${scheduler95p.toFixed(2)}ms
  ⚡ @Cacheable 95%: ${cacheable95p.toFixed(2)}ms
  🔄 Direct 95%: ${direct95p.toFixed(2)}ms

🚀 처리량 (TPS):
  🤖 스케줄러: ${schedulerTPS.toFixed(2)} req/s
  ⚡ @Cacheable: ${cacheableTPS.toFixed(2)} req/s
  🔄 Direct: ${directTPS.toFixed(2)} req/s

📋 요청 수 통계:
  🤖 스케줄러: ${schedulerCount}건
  ⚡ @Cacheable: ${cacheableCount}건  
  🔄 Direct: ${directCount}건
  📊 전체: ${totalRequests}건

🔍 성능 분석:
  📏 스케줄러 vs @Cacheable 차이: ${Math.abs(schedulerAvg - cacheableAvg).toFixed(2)}ms
  📏 @Cacheable vs Direct 차이: ${Math.abs(cacheableAvg - directAvg).toFixed(2)}ms
  📏 스케줄러 vs Direct 차이: ${Math.abs(schedulerAvg - directAvg).toFixed(2)}ms
  
  ${significant ? '✅ 방식 간 유의미한 성능 차이 확인됨' : '⚠️  방식 간 성능 차이가 미미함'}

💡 결론:
  ${winner === 'SCHEDULER' ? '🤖 스케줄러 방식이 가장 효율적입니다!' :
    winner === 'CACHEABLE' ? '⚡ @Cacheable 방식이 가장 효율적입니다!' :
    '🔄 Direct 방식이 의외로 가장 빠릅니다!'}
  
  ${!significant ? '⚠️  하지만 성능 차이가 크지 않으므로 구현 복잡도를 고려해야 합니다.' : ''}

🎯 권장사항:
  ${schedulerAvg > cacheableAvg + 20 ? '• 스케줄러 방식의 오버헤드가 큽니다. @Cacheable 사용 권장' : ''}
  ${cacheableAvg > directAvg + 30 ? '• @Cacheable 캐시 효과가 제한적입니다. 캐시 전략 재검토 필요' : ''}
  ${directAvg < Math.min(schedulerAvg, cacheableAvg) ? '• 캐시 오버헤드가 큽니다. 캐시 없이 사용 고려' : ''}
  ${significant ? '• 성능 차이가 명확하므로 가장 빠른 방식 선택 권장' : '• 성능보다는 유지보수성을 고려한 선택 권장'}

═══════════════════════════════════════════════════════════
    `
  };
}