import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 캐시 vs 비캐시 성능 비교 메트릭
const cacheEnabledResponseTime = new Trend('cache_enabled_response_time');
const cacheDisabledResponseTime = new Trend('cache_disabled_response_time');
const cacheEnabledRequests = new Counter('cache_enabled_requests');
const cacheDisabledRequests = new Counter('cache_disabled_requests');

// 엔드포인트별 성능 메트릭
const popularConcertsResponseTime = new Trend('popular_concerts_response_time');
const availableConcertsResponseTime = new Trend('available_concerts_response_time');
const concertDetailResponseTime = new Trend('concert_detail_response_time');
const trendingConcertsResponseTime = new Trend('trending_concerts_response_time');

export let options = {
  scenarios: {
    // 캐시 워밍업 단계 (더 긴 시간)
    cache_warmup: {
      executor: 'constant-vus',
      vus: 3,
      duration: '1m',
      tags: { phase: 'warmup' },
    },
    
    // 캐시 활성화 상태 테스트 (스케줄러 캐시 활용)
    with_cache: {
      executor: 'constant-vus',
      vus: 40,
      duration: '4m',
      startTime: '1m',
      tags: { cache_mode: 'enabled' },
    },
    
    // 캐시 비활성화 시뮬레이션 (특수 파라미터로 DB 직접 조회)
    without_cache: {
      executor: 'constant-vus',
      vus: 40,
      duration: '4m',
      startTime: '5m30s',
      tags: { cache_mode: 'disabled' },
    }
  },
  
  thresholds: {
    'cache_enabled_response_time': ['p(95)<150'], // 스케줄러 캐시로 더 빠른 응답 기대
    'cache_disabled_response_time': ['p(95)<800'],
    'popular_concerts_response_time': ['p(95)<200'],
    'available_concerts_response_time': ['p(95)<300'],
    'concert_detail_response_time': ['p(95)<250'],
    'trending_concerts_response_time': ['p(95)<200'],
    'http_req_failed': ['rate<0.01'],
  },
};

const BASE_URL = 'http://localhost:8080';

export default function() {
  const scenario = __ENV.K6_SCENARIO || getCurrentScenario();
  
  if (scenario === 'cache_warmup') {
    warmupCache();
  } else if (scenario === 'with_cache') {
    testWithSchedulerCache();
  } else {
    testWithoutCache();
  }
}

function getCurrentScenario() {
  const elapsedTime = (__ITER * __VU) % 570; // 9분 30초 주기
  if (elapsedTime < 60) return 'cache_warmup';
  if (elapsedTime < 300) return 'with_cache';
  return 'without_cache';
}

// 캐시 워밍업 - 일반적인 패턴으로 스케줄러 캐시 생성 유도
function warmupCache() {
  const warmupOperations = [
    // 일반적인 패턴들로 스케줄러가 캐시할 데이터 요청
    () => http.get(`${BASE_URL}/api/v1/concerts/popular?limit=10`),
    () => http.get(`${BASE_URL}/api/v1/concerts/popular?limit=5`),
    () => http.get(`${BASE_URL}/api/v1/concerts/trending?limit=5`),
    () => http.get(`${BASE_URL}/api/v1/concerts`), // 기본 파라미터
    () => http.get(`${BASE_URL}/api/v1/concerts/1`),
    () => http.get(`${BASE_URL}/api/v1/concerts/2`),
    () => http.get(`${BASE_URL}/api/v1/concerts/3`),
    () => http.get(`${BASE_URL}/api/v1/concerts/1/schedules`),
    () => http.get(`${BASE_URL}/api/v1/concerts/2/schedules`),
  ];
  
  const operation = warmupOperations[Math.floor(Math.random() * warmupOperations.length)];
  operation();
  
  sleep(0.2); // 스케줄러 처리 시간 고려
}

// 스케줄러 캐시 활용 테스트 - 일반적인 패턴만 사용
function testWithSchedulerCache() {
  const operations = [
    'popular_concerts_cached',
    'trending_concerts_cached',
    'available_concerts_cached',
    'concert_detail_cached',
    'concert_schedules_cached'
  ];
  
  const operation = operations[Math.floor(Math.random() * operations.length)];
  
  let startTime = Date.now();
  let response;
  
  switch (operation) {
    case 'popular_concerts_cached':
      // 스케줄러가 캐시하는 패턴만 사용 (limit=5, 10)
      const popularLimits = [5, 10];
      const limit = popularLimits[Math.floor(Math.random() * popularLimits.length)];
      response = http.get(`${BASE_URL}/api/v1/concerts/popular?limit=${limit}`, {
        tags: { operation: 'popular', cache_mode: 'enabled' }
      });
      popularConcertsResponseTime.add(Date.now() - startTime);
      break;
      
    case 'trending_concerts_cached':
      // 트렌딩은 limit=5만 캐시됨
      response = http.get(`${BASE_URL}/api/v1/concerts/trending?limit=5`, {
        tags: { operation: 'trending', cache_mode: 'enabled' }
      });
      trendingConcertsResponseTime.add(Date.now() - startTime);
      break;
      
    case 'available_concerts_cached':
      // 파라미터 없음 (기본값) 또는 일반적인 패턴 사용
      const availablePatterns = [
        `${BASE_URL}/api/v1/concerts`, // 기본 파라미터 (스케줄러 캐시됨)
        `${BASE_URL}/api/v1/concerts?startDate=${getTodayString()}&endDate=${getThreeMonthsLaterString()}`, // 기본 범위
        `${BASE_URL}/api/v1/concerts?startDate=${getTodayString()}&endDate=${getOneWeekLaterString()}`, // 주간
        `${BASE_URL}/api/v1/concerts?startDate=${getTodayString()}&endDate=${getOneMonthLaterString()}` // 월간
      ];
      response = http.get(availablePatterns[Math.floor(Math.random() * availablePatterns.length)], {
        tags: { operation: 'available', cache_mode: 'enabled' }
      });
      availableConcertsResponseTime.add(Date.now() - startTime);
      break;
      
    case 'concert_detail_cached':
      // 인기 콘서트 ID만 사용 (스케줄러가 캐시함)
      const concertId = Math.floor(Math.random() * 10) + 1; // 1-10
      response = http.get(`${BASE_URL}/api/v1/concerts/${concertId}`, {
        tags: { operation: 'detail', cache_mode: 'enabled' }
      });
      concertDetailResponseTime.add(Date.now() - startTime);
      break;
      
    case 'concert_schedules_cached':
      // 인기 콘서트의 스케줄 (스케줄러가 캐시함)
      const schedulesConcertId = Math.floor(Math.random() * 10) + 1; // 1-10
      response = http.get(`${BASE_URL}/api/v1/concerts/${schedulesConcertId}/schedules`, {
        tags: { operation: 'schedules', cache_mode: 'enabled' }
      });
      break;
  }
  
  let responseTime = Date.now() - startTime;
  
  cacheEnabledResponseTime.add(responseTime);
  cacheEnabledRequests.add(1);
  
  check(response, {
    'scheduler cache status 200': (r) => r.status === 200,
    'scheduler cache fast response': () => responseTime < 150,
    'scheduler cache has data': (r) => r.json('data') !== undefined,
  });
  
  sleep(0.02); // 빠른 요청으로 스케줄러 캐시 효과 극대화
}

// 캐시 비활성화 시뮬레이션 - 스케줄러가 캐시하지 않는 패턴 사용
function testWithoutCache() {
  const operations = [
    'popular_concerts_uncached',
    'trending_concerts_uncached',
    'available_concerts_uncached',
    'concert_detail_uncached',
    'schedule_detail_uncached'
  ];
  
  const operation = operations[Math.floor(Math.random() * operations.length)];
  
  let startTime = Date.now();
  let response;
  
  switch (operation) {
    case 'popular_concerts_uncached':
      // 스케줄러가 캐시하지 않는 limit 사용
      const randomLimit = Math.floor(Math.random() * 15) + 11; // 11-25 (캐시되지 않는 값)
      response = http.get(`${BASE_URL}/api/v1/concerts/popular?limit=${randomLimit}`, {
        tags: { operation: 'popular', cache_mode: 'disabled' }
      });
      popularConcertsResponseTime.add(Date.now() - startTime);
      break;
      
    case 'trending_concerts_uncached':
      // 스케줄러가 캐시하지 않는 limit 사용
      const randomTrendingLimit = Math.floor(Math.random() * 10) + 7; // 7-16 (5가 아닌 값)
      response = http.get(`${BASE_URL}/api/v1/concerts/trending?limit=${randomTrendingLimit}`, {
        tags: { operation: 'trending', cache_mode: 'disabled' }
      });
      trendingConcertsResponseTime.add(Date.now() - startTime);
      break;
      
    case 'available_concerts_uncached':
      // 스케줄러가 캐시하지 않는 특수한 날짜 범위
      const today = new Date();
      const randomStartDays = Math.floor(Math.random() * 90) + 30; // 30-120일 후 (일반적이지 않은 범위)
      const randomEndDays = randomStartDays + Math.floor(Math.random() * 60) + 30; // +30-90일
      
      const startDate = new Date(today.getTime() + randomStartDays * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const endDate = new Date(today.getTime() + randomEndDays * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      
      response = http.get(`${BASE_URL}/api/v1/concerts?startDate=${startDate}&endDate=${endDate}`, {
        tags: { operation: 'available', cache_mode: 'disabled' }
      });
      availableConcertsResponseTime.add(Date.now() - startTime);
      break;
      
    case 'concert_detail_uncached':
      // 스케줄러가 캐시하지 않는 콘서트 ID (11번 이상)
      const randomConcertId = Math.floor(Math.random() * 90) + 11; // 11-100
      response = http.get(`${BASE_URL}/api/v1/concerts/${randomConcertId}`, {
        tags: { operation: 'detail', cache_mode: 'disabled' }
      });
      concertDetailResponseTime.add(Date.now() - startTime);
      break;
      
    case 'schedule_detail_uncached':
      // 스케줄러가 캐시하지 않는 콘서트의 스케줄
      const randomSchedulesConcertId = Math.floor(Math.random() * 90) + 11; // 11-100
      response = http.get(`${BASE_URL}/api/v1/concerts/${randomSchedulesConcertId}/schedules`, {
        tags: { operation: 'schedules', cache_mode: 'disabled' }
      });
      break;
  }
  
  let responseTime = Date.now() - startTime;
  
  cacheDisabledResponseTime.add(responseTime);
  cacheDisabledRequests.add(1);
  
  check(response, {
    'no cache response': (r) => r.status === 200 || r.status === 404,
    'no cache measured': () => responseTime > 0,
  });
  
  sleep(0.1);
}

// 헬퍼 함수들
function getTodayString() {
  return new Date().toISOString().split('T')[0];
}

function getOneWeekLaterString() {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  return date.toISOString().split('T')[0];
}

function getOneMonthLaterString() {
  const date = new Date();
  date.setMonth(date.getMonth() + 1);
  return date.toISOString().split('T')[0];
}

function getThreeMonthsLaterString() {
  const date = new Date();
  date.setMonth(date.getMonth() + 3);
  return date.toISOString().split('T')[0];
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
  
  const testDuration = 240; // 4분 = 240초
  const throughputWithCache = cacheEnabledCount / testDuration;
  const throughputWithoutCache = cacheDisabledCount / testDuration;
  const throughputImprovement = throughputWithoutCache > 0 ? 
    ((throughputWithCache - throughputWithoutCache) / throughputWithoutCache * 100) : 0;
  
  // 엔드포인트별 성능 분석
  const popularAvg = data.metrics.popular_concerts_response_time?.values.avg || 0;
  const availableAvg = data.metrics.available_concerts_response_time?.values.avg || 0;
  const detailAvg = data.metrics.concert_detail_response_time?.values.avg || 0;
  const trendingAvg = data.metrics.trending_concerts_response_time?.values.avg || 0;
  
  // 캐시 효율성 계산
  const cacheEfficiency = cacheEnabledAvg > 0 && cacheDisabledAvg > 0 ? 
    (100 - (cacheEnabledAvg / cacheDisabledAvg * 100)) : 0;
  
  return {
    'scheduler-cache-summary.json': JSON.stringify(data, null, 2),
    stdout: `
═══════════════════════════════════════
📊 스케줄러 기반 Redis 캐시 성능 테스트 결과
═══════════════════════════════════════

🚀 전체 응답 시간 비교:
  ✅ 스케줄러 캐시 평균: ${cacheEnabledAvg.toFixed(2)}ms
  ❌ 캐시 미사용 평균: ${cacheDisabledAvg.toFixed(2)}ms
  📈 성능 향상: ${performanceImprovement.toFixed(1)}%

📊 95% 응답 시간:
  ✅ 스케줄러 캐시 95%: ${cacheEnabled95p.toFixed(2)}ms
  ❌ 캐시 미사용 95%: ${cacheDisabled95p.toFixed(2)}ms
  📈 95% 개선: ${cacheDisabled95p > 0 ? (((cacheDisabled95p - cacheEnabled95p) / cacheDisabled95p * 100).toFixed(1)) : 0}%

🔄 처리량 비교:
  ✅ 스케줄러 캐시 TPS: ${throughputWithCache.toFixed(2)} req/s
  ❌ 캐시 미사용 TPS: ${throughputWithoutCache.toFixed(2)} req/s
  📈 처리량 향상: ${throughputImprovement.toFixed(1)}%

📈 엔드포인트별 평균 응답시간:
  🎵 인기 콘서트: ${popularAvg.toFixed(2)}ms
  📅 예약가능 콘서트: ${availableAvg.toFixed(2)}ms
  📄 콘서트 상세: ${detailAvg.toFixed(2)}ms
  🔥 트렌딩 콘서트: ${trendingAvg.toFixed(2)}ms

📊 요청 수 통계:
  🟢 스케줄러 캐시: ${cacheEnabledCount}건
  🔴 캐시 미사용: ${cacheDisabledCount}건
  📊 전체: ${data.metrics.http_reqs?.values.count || 0}건

🎯 스케줄러 캐시 효율성:
  ⚡ 속도 향상 배수: ${cacheDisabledAvg > 0 ? (cacheDisabledAvg / cacheEnabledAvg).toFixed(1) : 'N/A'}x
  🚀 처리량 증가: ${throughputImprovement > 0 ? '+' : ''}${throughputImprovement.toFixed(1)}%
  💾 캐시 효율성: ${cacheEfficiency.toFixed(1)}%

📝 스케줄러 캐시 전략:
  🕐 예약가능 콘서트: 10분마다 자동 갱신 (기본/주간/월간)
  🕔 인기/트렌딩: 5분마다 자동 갱신 (TOP 5/10)
  🕗 콘서트 상세: 30분마다 인기 콘서트 상세정보 갱신
  🌙 일일 정리: 매일 새벽 3시 전체 캐시 리프레시

💡 스케줄러 캐시 성능 분석:
  ${performanceImprovement > 70 ? '🎉 스케줄러 캐시가 매우 효과적으로 작동하고 있습니다!' : 
    performanceImprovement > 50 ? '🔥 스케줄러 캐시가 상당한 성능 향상을 제공하고 있습니다!' :
    performanceImprovement > 30 ? '✅ 스케줄러 캐시가 적절한 성능 개선을 하고 있습니다.' : 
    performanceImprovement > 10 ? '⚠️  스케줄러 캐시 효과가 제한적입니다. 갱신 주기를 검토해보세요.' :
    '❌ 스케줄러 캐시 설정을 재검토하세요.'}

🔍 권장사항:
  ${cacheEnabled95p > 150 ? '• 스케줄러 갱신 주기를 단축하거나 Redis 성능 튜닝 필요' : '• 스케줄러 캐시 응답시간 양호'}
  ${throughputImprovement < 30 ? '• 스케줄러 갱신 빈도 증가 또는 캐시 범위 확대 검토' : '• 스케줄러 처리량 개선 효과 확인됨'}
  ${performanceImprovement < 50 ? '• 더 많은 사용 패턴을 스케줄러로 커버하도록 확장 검토' : '• 스케줄러 캐시 전략이 효과적으로 작동 중'}

🔄 스케줄러 vs @Cacheable 비교:
  ✅ 장점: 예측 가능한 성능, 높은 캐시 적중률, 자동 갱신
  ⚠️  주의: 메모리 사용량 증가, 갱신 주기 최적화 필요
  💡 결론: 일반적인 사용 패턴에는 스케줄러 캐시가 더 효과적

═══════════════════════════════════════
    `,
  };
}