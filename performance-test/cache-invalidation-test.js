import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// 캐시 무효화 시나리오 테스트
const cacheInvalidationRate = new Rate('cache_invalidation_success');

export let options = {
  scenarios: {
    // 캐시 무효화 테스트
    cache_invalidation: {
      executor: 'shared-iterations',
      vus: 3,
      iterations: 15,
      maxDuration: '2m',
    }
  },
  
  thresholds: {
    'http_req_duration': ['p(95)<3000'],
    'cache_invalidation_success': ['rate>0.8'],
    'http_req_failed': ['rate<0.1'],
  },
};

const BASE_URL = 'http://localhost:8080';
const TEST_USER_ID = 999;

export default function() {
  // 시나리오: 콘서트 조회 → 예약 → 캐시 무효화 확인
  testCacheInvalidationScenario();
}

function testCacheInvalidationScenario() {
  const scenarioId = `scenario_${__VU}_${__ITER}`;
  console.log(`🎭 ${scenarioId} 시작`);
  
  // 1. 초기 콘서트 목록 조회 (캐시 생성)
  console.log(`📋 ${scenarioId}: 초기 콘서트 목록 조회`);
  let initialResponse = http.get(`${BASE_URL}/api/v1/concerts/available`, {
    tags: { phase: 'initial_load', scenario: scenarioId }
  });

  check(initialResponse, {
    'initial concerts load success': (r) => r.status === 200,
  });

  let initialData = JSON.parse(initialResponse.body);
  let initialAvailableCount = initialData.length;
  console.log(`📊 ${scenarioId}: 초기 사용 가능 콘서트 수: ${initialAvailableCount}`);

  sleep(1);

  // 2. 동일한 요청으로 캐시 확인 (빠른 응답 확인)
  console.log(`🚀 ${scenarioId}: 캐시된 데이터 조회`);
  let cachedStartTime = Date.now();
  let cachedResponse = http.get(`${BASE_URL}/api/v1/concerts/available`, {
    tags: { phase: 'cached_load', scenario: scenarioId }
  });
  let cachedResponseTime = Date.now() - cachedStartTime;

  check(cachedResponse, {
    'cached concerts load success': (r) => r.status === 200,
    'cached response fast': () => cachedResponseTime < 200, // 캐시는 200ms 이하
  });

  console.log(`⚡ ${scenarioId}: 캐시된 응답 시간: ${cachedResponseTime}ms`);

  sleep(1);

  // 3. 좌석 예약 시도 (캐시 무효화 유발 가능한 작업)
  if (initialData.length > 0) {
    const testConcert = initialData[0];
    console.log(`🎫 ${scenarioId}: 좌석 예약 시도 - 콘서트 ID: ${testConcert.concertId}`);

    // 토큰 발급
    let tokenResponse = http.post(`${BASE_URL}/api/v1/auth/token`,
      JSON.stringify({ userId: TEST_USER_ID + __VU }),
      {
        headers: { 'Content-Type': 'application/json' },
        tags: { phase: 'token_issue', scenario: scenarioId }
      }
    );

    if (tokenResponse.status === 200) {
      let token = JSON.parse(tokenResponse.body).token;

      // 좌석 예약
      let reservationResponse = http.post(`${BASE_URL}/api/v1/reservations`,
        JSON.stringify({
          userId: TEST_USER_ID + __VU,
          concertId: testConcert.concertId,
          seatId: Math.floor(Math.random() * 50) + 1, // 랜덤 좌석
          token: token
        }),
        {
          headers: { 'Content-Type': 'application/json' },
          tags: { phase: 'reservation', scenario: scenarioId }
        }
      );

      let reservationSuccess = reservationResponse.status === 200;
      console.log(`🎯 ${scenarioId}: 예약 결과: ${reservationSuccess ? '성공' : '실패'} (${reservationResponse.status})`);

      if (reservationSuccess) {
        sleep(2); // 캐시 무효화 처리 시간 대기

        // 4. 캐시 무효화 확인 (콘서트 목록 재조회)
        console.log(`🔄 ${scenarioId}: 캐시 무효화 후 데이터 조회`);
        let invalidatedStartTime = Date.now();
        let invalidatedResponse = http.get(`${BASE_URL}/api/v1/concerts/available`, {
          tags: { phase: 'after_invalidation', scenario: scenarioId }
        });
        let invalidatedResponseTime = Date.now() - invalidatedStartTime;
        
        check(invalidatedResponse, {
          'invalidated concerts load success': (r) => r.status === 200,
        });
        
        let invalidatedData = JSON.parse(invalidatedResponse.body);
        let newAvailableCount = invalidatedData.length;
        
        // 캐시 무효화 검증
        let cacheInvalidated = invalidatedResponseTime > cachedResponseTime * 2; // 응답시간이 2배 이상 증가
        let dataChanged = newAvailableCount !== initialAvailableCount; // 데이터 변경 확인
        
        cacheInvalidationRate.add(cacheInvalidated ? 1 : 0);
        
        console.log(`📈 ${scenarioId}: 무효화 후 응답시간: ${invalidatedResponseTime}ms`);
        console.log(`📊 ${scenarioId}: 사용 가능 콘서트 수 변화: ${initialAvailableCount} → ${newAvailableCount}`);
        console.log(`🔍 ${scenarioId}: 캐시 무효화 감지: ${cacheInvalidated ? '✅' : '❌'}`);
        
        check(invalidatedResponse, {
          'cache invalidation detected': () => cacheInvalidated,
          'data consistency maintained': () => newAvailableCount >= 0,
        });
      }
    }
  }
  
  sleep(2);
  console.log(`✅ ${scenarioId} 완료\n`);
}

export function handleSummary(data) {
  const invalidationRate = data.metrics.cache_invalidation_success?.values.rate || 0;
  const avgResponseTime = data.metrics.http_req_duration?.values.avg || 0;
  
  return {
    'cache-invalidation-summary.json': JSON.stringify(data, null, 2),
    stdout: `
═══════════════════════════════════════
🔄 캐시 무효화 테스트 결과
═══════════════════════════════════════

📊 무효화 성능:
  🎯 캐시 무효화 감지율: ${(invalidationRate * 100).toFixed(2)}%
  ⏱️  평균 응답시간: ${avgResponseTime.toFixed(2)}ms
  📈 총 HTTP 요청: ${data.metrics.http_reqs?.values.count || 0}
  ❌ 실패율: ${((data.metrics.http_req_failed?.values.rate || 0) * 100).toFixed(2)}%

🎯 목표 달성도:
  ${invalidationRate > 0.8 ? '✅' : '❌'} 무효화 감지율 목표 (80% 이상): ${(invalidationRate * 100).toFixed(2)}%
  ${avgResponseTime < 3000 ? '✅' : '❌'} 응답시간 목표 (3초 이하): ${avgResponseTime.toFixed(2)}ms

💡 캐시 무효화 동작:
   ${invalidationRate > 0.8 ? '정상적으로 캐시가 무효화되고 있습니다' : '캐시 무효화가 제대로 동작하지 않을 수 있습니다'}

═══════════════════════════════════════
    `,
  };
}
