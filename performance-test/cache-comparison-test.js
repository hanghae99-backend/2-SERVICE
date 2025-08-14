import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 캐시 vs 비캐시 성능 비교 메트릭
const cacheEnabledResponseTime = new Trend('cache_enabled_response_time');
const cacheDisabledResponseTime = new Trend('cache_disabled_response_time');
const cacheEnabledRequests = new Counter('cache_enabled_requests');
const cacheDisabledRequests = new Counter('cache_disabled_requests');

export let options = {
  scenarios: {
    // 캐시 활성화 상태 테스트
    with_cache: {
      executor: 'constant-vus',
      vus: 20,
      duration: '2m',
      tags: { cache_mode: 'enabled' },
    },
    
    // 캐시 비활성화 상태 테스트 (캐시 키에 랜덤값 추가로 시뮬레이션)
    without_cache: {
      executor: 'constant-vus',
      vus: 20,
      duration: '2m',
      startTime: '30s', // 캐시 테스트 완료 후 시작
      tags: { cache_mode: 'disabled' },
    }
  },
  
  thresholds: {
    'cache_enabled_response_time': ['p(95)<500'],
    'cache_disabled_response_time': ['p(95)<2000'],
    'http_req_failed': ['rate<0.05'],
  },
};

const BASE_URL = 'http://localhost:8080';

export default function() {
  const scenario = __ENV.K6_SCENARIO || (__ITER < 120 ? 'with_cache' : 'without_cache');
  
  if (scenario === 'with_cache') {
    testWithCache();
  } else {
    testWithoutCache();
  }
}

// 캐시 활성화 테스트 (동일한 파라미터로 반복 요청)
function testWithCache() {
  const operations = [
    'popular_concerts',
    'available_concerts',
    'concert_detail',
    'trending_concerts'
  ];
  
  const operation = operations[Math.floor(Math.random() * operations.length)];
  
  let startTime = Date.now();
  let response;
  
  switch (operation) {
    case 'popular_concerts':
      response = http.get(`${BASE_URL}/api/v1/concerts/popular?limit=10`, {
        tags: { operation: 'popular', cache_mode: 'enabled' }
      });
      break;
      
    case 'concerts':
      response = http.get(`${BASE_URL}/api/v1/concerts`, {
        tags: { operation: 'available', cache_mode: 'enabled' }
      });
      break;
      
    case 'concert_detail':
      const concertId = [1, 2, 3, 4, 5][Math.floor(Math.random() * 5)]; // 고정된 ID들
      response = http.get(`${BASE_URL}/api/v1/concerts/${concertId}`, {
        tags: { operation: 'detail', cache_mode: 'enabled' }
      });
      break;
      
    case 'trending_concerts':
      response = http.get(`${BASE_URL}/api/v1/concerts/trending?limit=5`, {
        tags: { operation: 'trending', cache_mode: 'enabled' }
      });
      break;
  }
  
  let responseTime = Date.now() - startTime;
  
  cacheEnabledResponseTime.add(responseTime);
  cacheEnabledRequests.add(1);
  
  check(response, {
    'cache enabled status 200': (r) => r.status === 200,
    'cache enabled fast response': () => responseTime < 500,
  });
  
  sleep(0.1);
}

// 캐시 비활성화 시뮬레이션 (매번 다른 파라미터로 캐시 미스 유발)
function testWithoutCache() {
  const operations = [
    'popular_concerts_random',
    'available_concerts_random',
    'concert_detail_random',
    'schedule_detail_random'
  ];
  
  const operation = operations[Math.floor(Math.random() * operations.length)];
  
  let startTime = Date.now();
  let response;
  
  switch (operation) {
    case 'popular_concerts_random':
      const randomLimit = Math.floor(Math.random() * 20) + 5; // 5-25
      response = http.get(`${BASE_URL}/api/v1/concerts/popular?limit=${randomLimit}&_cache_bust=${Date.now()}`, {
        tags: { operation: 'popular', cache_mode: 'disabled' }
      });
      break;
      
    case 'available_concerts_random':
      const randomDays = Math.floor(Math.random() * 90) + 1; // 1-90일
      response = http.get(`${BASE_URL}/api/v1/concerts/available?days=${randomDays}&_cache_bust=${Date.now()}`, {
        tags: { operation: 'available', cache_mode: 'disabled' }
      });
      break;
      
    case 'concert_detail_random':
      const randomConcertId = Math.floor(Math.random() * 100) + 1; // 1-100
      response = http.get(`${BASE_URL}/api/v1/concerts/${randomConcertId}?_cache_bust=${Date.now()}`, {
        tags: { operation: 'detail', cache_mode: 'disabled' }
      });
      break;
      
    case 'schedule_detail_random':
      const randomScheduleId = Math.floor(Math.random() * 50) + 1; // 1-50
      response = http.get(`${BASE_URL}/api/v1/concerts/schedule/${randomScheduleId}/detail?_cache_bust=${Date.now()}`, {
        tags: { operation: 'schedule_detail', cache_mode: 'disabled' }
      });
      break;
  }
  
  let responseTime = Date.now() - startTime;
  
  cacheDisabledResponseTime.add(responseTime);
  cacheDisabledRequests.add(1);
  
  check(response, {
    'cache disabled response': (r) => r.status === 200 || r.status === 404, // 404도 정상 (랜덤 ID이므로)
    'cache disabled measured': () => responseTime > 0,
  });
  
  sleep(0.2);
}

export function handleSummary(data) {
  const cacheEnabledAvg = data.metrics.cache_enabled_response_time?.values.avg || 0;
  const cacheDisabledAvg = data.metrics.cache_disabled_response_time?.values.avg || 0;
  const cacheEnabled95p = data.metrics.cache_enabled_response_time?.values['p(95)'] || 0;
  const cacheDisabled95p = data.metrics.cache_disabled_response_time?.values['p(95)'] || 0;
  
  const cacheEnabledCount = data.metrics.cache_enabled_requests?.values.count || 0;
  const cacheDisabledCount = data.metrics.cache_disabled_requests?.values.count || 0;
  
  const performanceImprovement = cacheDisabledAvg > 0 ? 
    ((cacheDisabledAvg - cacheEnabledAvg) / cacheDisabledAvg * 100) : 0;
  
  const throughputWithCache = cacheEnabledCount / 120; // 2분 = 120초
  const throughputWithoutCache = cacheDisabledCount / 120;
  const throughputImprovement = throughputWithoutCache > 0 ? 
    ((throughputWithCache - throughputWithoutCache) / throughputWithoutCache * 100) : 0;
  
  return {
    'cache-comparison-summary.json': JSON.stringify(data, null, 2),
    stdout: `
═══════════════════════════════════════
📊 캐시 vs 비캐시 성능 비교 결과
═══════════════════════════════════════

🚀 응답 시간 비교:
  ✅ 캐시 활성화 평균: ${cacheEnabledAvg.toFixed(2)}ms
  ❌ 캐시 비활성화 평균: ${cacheDisabledAvg.toFixed(2)}ms
  📈 성능 향상: ${performanceImprovement.toFixed(1)}%

📊 95% 응답 시간:
  ✅ 캐시 활성화 95%: ${cacheEnabled95p.toFixed(2)}ms
  ❌ 캐시 비활성화 95%: ${cacheDisabled95p.toFixed(2)}ms
  📈 95% 개선: ${cacheDisabled95p > 0 ? (((cacheDisabled95p - cacheEnabled95p) / cacheDisabled95p * 100).toFixed(1)) : 0}%

🔄 처리량 비교:
  ✅ 캐시 활성화 TPS: ${throughputWithCache.toFixed(2)} req/s
  ❌ 캐시 비활성화 TPS: ${throughputWithoutCache.toFixed(2)} req/s
  📈 처리량 향상: ${throughputImprovement.toFixed(1)}%

📈 총 요청 수:
  🟢 캐시 활성화: ${cacheEnabledCount}건
  🔴 캐시 비활성화: ${cacheDisabledCount}건
  📊 전체: ${data.metrics.http_reqs?.values.count || 0}건

🎯 캐시 효율성:
  ⚡ 속도 향상 배수: ${cacheDisabledAvg > 0 ? (cacheDisabledAvg / cacheEnabledAvg).toFixed(1) : 'N/A'}x
  🚀 처리량 증가: ${throughputImprovement > 0 ? '+' : ''}${throughputImprovement.toFixed(1)}%
  
💡 결론:
  ${performanceImprovement > 50 ? '🎉 캐시가 매우 효과적으로 작동하고 있습니다!' : 
    performanceImprovement > 20 ? '✅ 캐시가 적절히 성능을 개선하고 있습니다.' : 
    '⚠️  캐시 효과가 제한적입니다. 캐시 전략을 검토해보세요.'}

═══════════════════════════════════════
    `,
  };
}
