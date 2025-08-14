import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

// 커스텀 메트릭
const lockConflicts = new Rate('lock_conflicts');
const successfulOperations = new Counter('successful_operations');
const multiLockSuccesses = new Counter('multi_lock_successes');
const dataConsistency = new Rate('data_consistency');

export let options = {
  scenarios: {
    // 1. 잔액 충전 동시성 (단일 락)
    // balance_charge_concurrency: {
    //   executor: 'per-vu-iterations',
    //   vus: 10,
    //   iterations: 2,
    //   maxDuration: '30s',
    //   tags: { test_type: 'balance_charge', lock_type: 'single' },
    //   startTime: '0s',
    // },

    // 2. 좌석 예약 동시성 (단일 락) - 동시성 제어 검증
    seat_reservation_concurrency: {
      executor: 'per-vu-iterations',
      vus: 5, // 5명이 동시에 같은 좌석 예약 시도
      iterations: 1,
      maxDuration: '60s',
      tags: { test_type: 'seat_reservation', lock_type: 'single', test_strategy: 'conflict_expected' },
      startTime: '0s',
    },

    // // 3. 토큰 발급 동시성 (단일 락)
    // token_issue_concurrency: {
    //   executor: 'per-vu-iterations',
    //   vus: 10,
    //   iterations: 3,
    //   maxDuration: '45s',
    //   tags: { test_type: 'token_issue', lock_type: 'single' },
    //   startTime: '100s',
    // },
    //
    // // 4. 결제 처리 동시성 (멀티 락 - 핵심!)
    // payment_process_concurrency: {
    //   executor: 'per-vu-iterations',
    //   vus: 15, // VU 수 줄여서 안정성 확보
    //   iterations: 1, // iteration 줄여서 빠른 완료
    //   maxDuration: '120s', // 토큰 활성화 시간 고려
    //   tags: { test_type: 'payment_process', lock_type: 'multi' },
    //   startTime: '150s',
    // },
    //
    // // 5. 예약 취소 동시성 (단일 락)
    // reservation_cancel_concurrency: {
    //   executor: 'per-vu-iterations',
    //   vus: 10,
    //   iterations: 2,
    //   maxDuration: '90s', // 토큰 활성화 시간 고려
    //   tags: { test_type: 'reservation_cancel', lock_type: 'single' },
    //   startTime: '280s',
    // },
  },
};

const BASE_URL = 'http://localhost:8080';

// 테스트 데이터 - DB의 실제 데이터와 일치하도록 수정
const TEST_USERS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]; // data.sql에 정의된 사용자 ID
// AVAILABLE 상태의 좌석들만 선택 (schedule_id=1 기준)
// data.sql에서 OCCUPIED: '01','02','03','10','11','15','20','25','30','35','40','45','50'
// RESERVED: '04','05'
// 따라서 AVAILABLE한 좌석 ID들만 사용
const AVAILABLE_SEATS = [6, 7, 8, 9, 12, 13, 14, 16, 17, 18, 19, 21, 22, 23, 24, 26, 27, 28, 29, 31, 32, 33, 34, 36, 37, 38, 39, 41, 42, 43, 44, 46, 47, 48, 49]; // AVAILABLE 상태 좌석 ID들
const TEST_CONCERT_ID = 1; // IU 콘서트
const TEST_SCHEDULE_ID = 1; // 첫 번째 스케줄

// 전역 상태 관리 - 예약된 좌석 추적 추가
let reservedSeats = new Set();
let testStats = {
  balance_charge: { attempts: 0, successes: 0, conflicts: 0 },
  seat_reservation: { attempts: 0, successes: 0, conflicts: 0 },
  token_issue: { attempts: 0, successes: 0, conflicts: 0 },
  payment_process: { attempts: 0, successes: 0, conflicts: 0 },
  mixed_operations: { attempts: 0, successes: 0, conflicts: 0 },
};

export default function() {
  const scenarioName = exec.scenario.name;
  const testType = getTestTypeFromScenario(scenarioName);
  const lockType = getLockTypeFromScenario(scenarioName);
  
  console.log(`🧪 [${testType}] ${lockType} 락 테스트 시작 - VU: ${__VU}`);
  
  switch (testType) {
    case 'balance_charge':
      testBalanceCharge();
      break;
    case 'seat_reservation':
      testSeatReservation();
      break;
    case 'token_issue':
      testTokenIssue();
      break;
    case 'payment_process':
      testPaymentProcess(); // 멀티 락!
      break;
    case 'mixed_operations':
      testMixedOperations();
      break;
  }
}

// 시나리오 이름에서 테스트 타입 추출
function getTestTypeFromScenario(scenarioName) {
  if (scenarioName.includes('balance_charge')) return 'balance_charge';
  if (scenarioName.includes('seat_reservation')) return 'seat_reservation';
  if (scenarioName.includes('token_issue')) return 'token_issue';
  if (scenarioName.includes('payment_process')) return 'payment_process';
  if (scenarioName.includes('mixed_operations')) return 'mixed_operations';
  return 'unknown';
}

// 시나리오 이름에서 락 타입 추출
function getLockTypeFromScenario(scenarioName) {
  if (scenarioName.includes('payment_process')) return 'multi';
  if (scenarioName.includes('mixed_operations')) return 'mixed';
  return 'single';
}

// 1. 잔액 충전 테스트 (단일 락: balance:#userId)
function testBalanceCharge() {
  const userId = TEST_USERS[(__VU - 1) % TEST_USERS.length];
  
  const result = performRequest('POST', `/api/v1/balance`, {
    userId: userId,
    amount: "1000.0"
  }, 'balance_charge');
  
  updateStats('balance_charge', result);
  sleep(0.3);
}

// 2. 좌석 예약 테스트 (단일 락: seat:#seatId) - 동시성 제어 검증
function testSeatReservation() {
  const userId = TEST_USERS[(__VU - 1) % TEST_USERS.length];
  
  // 🔥 동시성 테스트: 모든 VU가 같은 좌석에 동시 접근
  // 기대 결과: 첫 번째만 성공, 나머지는 락 충돌로 실패
  const targetSeatId = AVAILABLE_SEATS[0]; // 모든 VU가 같은 좌석 선택
  
  console.log(`🎫 [동시성 테스트] 좌석 예약 시도 - VU: ${__VU}, 사용자: ${userId}, 타깃 좌석: ${targetSeatId}`);
  
  // 1단계: 토큰 발급
  const token = issueTokenForUser(userId);
  if (!token) {
    console.log(`❌ 토큰 발급 실패 - 사용자: ${userId}`);
    updateStats('seat_reservation', { success: false, status: 0, duration: 0, isLockConflict: false, body: 'Token issue failed' });
    return;
  }
  
  // 2단계: 토큰 활성화 대기
  const isActive = waitForTokenActivation(token, userId, 30000);
  if (!isActive) {
    console.log(`❌ 토큰 활성화 실패 - 사용자: ${userId}`);
    updateStats('seat_reservation', { success: false, status: 0, duration: 0, isLockConflict: false, body: 'Token activation failed' });
    return;
  }
  
  // 3단계: 동시 예약 요청 (락 충돌 예상)
  console.log(`🎫 [동시성] 예약 요청 시작 - VU: ${__VU}, 사용자: ${userId}, 좌석: ${targetSeatId}`);
  
  const startTime = Date.now();
  const result = performRequest('POST', `/api/v1/reservations`, {
    userId: userId,
    concertId: TEST_CONCERT_ID,
    scheduleId: TEST_SCHEDULE_ID,
    seatId: targetSeatId,
    token: token
  }, 'seat_reservation');
  const endTime = Date.now();

  // 결과 분석
  if (result.success) {
    console.log(`✅ [동시성 성공] 좌석 예약 성공! - VU: ${__VU}, 사용자: ${userId}, 좌석: ${targetSeatId}, 소요시간: ${endTime - startTime}ms`);
    reservedSeats.add(targetSeatId);
  } else {
    // 실패 원인 분석 (락 충돌 예상)
    if (result.status === 409 || result.body.includes('이미') || result.body.includes('already')) {
      console.log(`⚡ [동시성 예상 실패] 좌석 이미 예약됨 - VU: ${__VU}, 사용자: ${userId}, 좌석: ${targetSeatId}, 소요시간: ${endTime - startTime}ms`);
    } else if (result.isLockConflict || result.status >= 500) {
      console.log(`🔥 [동시성 예상 실패] 락 충돌 발생 - VU: ${__VU}, 사용자: ${userId}, 좌석: ${targetSeatId}, 상태: ${result.status}, 소요시간: ${endTime - startTime}ms`);
    } else {
      console.log(`❌ [동시성 비예상 실패] 기타 예약 실패 - VU: ${__VU}, 사용자: ${userId}, 좌석: ${targetSeatId}, 상태: ${result.status}`);
      console.log(`   응답: ${result.body.substring(0, 200)}`);
    }
  }
  
  updateStats('seat_reservation', result);
  sleep(0.5);
}

// 3. 토큰 발급 테스트 (단일 락: token:issue:#userId)
function testTokenIssue() {
  const userId = TEST_USERS[(__VU - 1) % TEST_USERS.length];
  
  const result = performRequest('POST', `/api/v1/tokens`, {
    userId: userId
  }, 'token_issue');
  
  updateStats('token_issue', result);
  sleep(0.2);
}

// 4. 결제 처리 테스트 (멀티 락: balance:#userId + reservation:#reservationId)
function testPaymentProcess() {
  const userId = TEST_USERS[(__VU - 1) % TEST_USERS.length];
  
  console.log(`💳 결제 프로세스 시작 - 사용자: ${userId}`);
  
  // 1단계: 잔액 충전 (먼저 충전)
  console.log(`💰 잔액 충전 시작 - 사용자: ${userId}`);
  chargeBalance(userId, 50000); // 5만원 충전
  
  // 2단계: 토큰 발급
  const token = issueTokenForUser(userId);
  if (!token) {
    console.log(`❌ 토큰 발급 실패 - 사용자: ${userId}`);
    return;
  }
  
  // 3단계: 토큰 활성화 대기
  const isActive = waitForTokenActivation(token, userId, 30000);
  if (!isActive) {
    console.log(`❌ 토큰 활성화 실패 - 사용자: ${userId}`);
    return;
  }
  
  // 4단계: 좌석 예약
  const seatId = AVAILABLE_SEATS[Math.floor(Math.random() * AVAILABLE_SEATS.length)];
  console.log(`🎫 좌석 예약 시작 - 사용자: ${userId}, 좌석: ${seatId}`);
  const reservation = createReservation(userId, seatId, token);
  if (!reservation) {
    console.log(`❌ 좌석 예약 실패 - 사용자: ${userId}`);
    return;
  }
  
  // 5단계: 결제 처리 (멀티 락 테스트!)
  console.log(`💳 결제 처리 시작 - 사용자: ${userId}, 예약: ${reservation.reservationId}`);
  const result = performRequest('POST', `/api/v1/payments`, {
    userId: userId,
    reservationId: reservation.reservationId,
    token: token
  }, 'payment_process', '60s'); // 타임아웃 증가
  
  updateStats('payment_process', result);
  
  if (result.success) {
    multiLockSuccesses.add(1);
    console.log(`✅ [멀티락] 결제 성공 - 사용자: ${userId}, 예약: ${reservation.reservationId}`);
  } else {
    console.log(`❌ [멀티락] 결제 실패 - 사용자: ${userId}, 상태: ${result.status}, 응답: ${result.body}`);
  }
  
  sleep(1);
}

// 6. 혼합 시나리오 테스트
function testMixedOperations() {
  const operations = ['charge', 'token', 'reservation'];
  const operation = operations[Math.floor(Math.random() * operations.length)];
  const userId = TEST_USERS[(__VU - 1) % TEST_USERS.length];
  
  let result;
  switch (operation) {
    case 'charge':
      result = performRequest('POST', `/api/v1/balance`, {
        userId: userId,
        amount: "500.0"
      }, 'mixed_charge');
      break;
    case 'token':
      result = performRequest('POST', `/api/v1/tokens`, {
        userId: userId
      }, 'mixed_token');
      break;
    case 'reservation':
      const token = issueTokenForUser(userId);
      if (token) {
        const seatId = AVAILABLE_SEATS[Math.floor(Math.random() * AVAILABLE_SEATS.length)];
        result = performRequest('POST', `/api/v1/reservations`, {
          userId: userId,
          concertId: TEST_CONCERT_ID,
          scheduleId: TEST_SCHEDULE_ID,
          seatId: seatId,
          token: token
        }, 'mixed_reservation');
      }
      break;
  }
  
  if (result) {
    updateStats('mixed_operations', result);
  }
  
  sleep(0.2 + Math.random() * 0.3);
}

// 공통 요청 함수
function performRequest(method, endpoint, payload, operation, timeout = '20s') {
  const startTime = Date.now();
  
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  };
  
  let response;
  if (method === 'POST') {
    response = http.post(`${BASE_URL}${endpoint}`, 
      JSON.stringify(payload), 
      {
        headers: headers,
        tags: { operation: operation },
        timeout: timeout
      }
    );
  } else if (method === 'DELETE') {
    response = http.del(`${BASE_URL}${endpoint}`, 
      JSON.stringify(payload), 
      {
        headers: headers,
        tags: { operation: operation },
        timeout: timeout
      }
    );
  }
  
  const duration = Date.now() - startTime;
  const success = response.status >= 200 && response.status < 300;
  
  return {
    success: success,
    status: response.status,
    duration: duration,
    isLockConflict: isLockConflictError(response),
    body: response.body
  };
}

// 락 충돌 에러 판별 - 개선된 버전
function isLockConflictError(response) {
  // 서버 내부 오류는 락 충돌 가능성 높음
  if (response.status >= 500) return true;
  
  // 응답 본문에서 분산락 관련 키워드 확인
  if (response.body) {
    const lockErrorKeywords = [
      'ConcurrentAccessException',
      'Lock 획득 실패',
      'DistributedLock',
      'timeout',
      'TimeoutException',
      '분산락',
      'lock acquisition failed',
      'already reserved',
      'seat is not available'
    ];
    
    const bodyLower = response.body.toLowerCase();
    return lockErrorKeywords.some(keyword => 
      bodyLower.includes(keyword.toLowerCase())
    );
  }
  
  return false;
}

// 통계 업데이트
function updateStats(testType, result) {
  if (testStats[testType]) {
    testStats[testType].attempts++;
    if (result.success) {
      testStats[testType].successes++;
      successfulOperations.add(1);
    }
    if (result.isLockConflict) {
      testStats[testType].conflicts++;
      lockConflicts.add(1);
    }
  }
}

// 토큰 발급 함수 (디버깅 강화)
function issueTokenForUser(userId) {
  console.log(`🔍 토큰 발급 시도 - 사용자: ${userId}`);
  
  const response = http.post(`${BASE_URL}/api/v1/tokens`, 
    JSON.stringify({ userId: userId }), 
    {
      headers: { 
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      timeout: '10s'
    }
  );
  
  console.log(`📋 토큰 응답 - 사용자: ${userId}, 상태: ${response.status}`);
  
  if (response.status === 200 || response.status === 201) {
    try {
      const data = JSON.parse(response.body);
      // CommonApiResponse 구조: { success, data: { token, status, ... }, message }
      const token = data.data?.token;
      const status = data.data?.status;
      
      if (token) {
        console.log(`✅ 토큰 발급 성공 - 사용자: ${userId}, 상태: ${status}`);
        return token;
      } else {
        console.log(`❌ 토큰 없음 - 사용자: ${userId}, 데이터: ${JSON.stringify(data)}`);
        return null;
      }
    } catch (e) {
      console.log(`❌ 토큰 파싱 실패 - 사용자: ${userId}, 오류: ${e.message}`);
      console.log(`🔴 응답 형식이 JSON이 아닐 수 있습니다. 응답 일부: ${response.body.substring(0, 100)}`);
      return null;
    }
  }
  console.log(`❌ 토큰 발급 실패 - 사용자: ${userId}, 상태: ${response.status}`);
  return null;
}

// 토큰 상태 확인 함수 (디버깅 강화)
function checkTokenStatus(token) {
  const response = http.get(`${BASE_URL}/api/v1/tokens/${token}`, 
    {
      headers: { 
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      timeout: '5s'
    }
  );
  
  if (response.status === 200) {
    try {
      const data = JSON.parse(response.body);
      // CommonApiResponse 구조: { success, data: { status, ... }, message }
      const status = data.data?.status;
      return status;
    } catch (e) {
      console.log(`❌ 토큰 상태 파싱 실패: ${e.message}`);
      console.log(`🔴 응답이 JSON이 아닐 수 있습니다. 응답 일부: ${response.body.substring(0, 100)}`);
      return null;
    }
  }
  console.log(`❌ 토큰 상태 확인 실패 - 상태: ${response.status}`);
  return null;
}

// 토큰 활성화 대기 함수 (스케줄러가 5초마다 처리하므로 더 짧은 간격으로 체크)
function waitForTokenActivation(token, userId, maxWaitTime = 20000) {
  console.log(`⏳ 토큰 활성화 대기 시작 - 사용자: ${userId}`);
  
  const startTime = Date.now();
  let attempts = 0;
  
  while (Date.now() - startTime < maxWaitTime) {
    attempts++;
    const status = checkTokenStatus(token);
    console.log(`🔄 토큰 상태 확인 (${attempts}회) - 사용자: ${userId}, 상태: ${status}`);
    
    if (status === 'ACTIVE') {
      console.log(`🎯 토큰 활성화 완료! - 사용자: ${userId}, 대기시간: ${Date.now() - startTime}ms`);
      return true;
    }
    
    if (status === 'EXPIRED' || status === 'CANCELLED') {
      console.log(`❌ 토큰이 만료/취소됨 - 사용자: ${userId}, 상태: ${status}`);
      return false;
    }
    
    // 1초마다 확인 (더 빠른 반응을 위해)
    sleep(1);
  }
  
  console.log(`⏰ 토큰 활성화 대기 시간 초과 - 사용자: ${userId}`);
  return false;
}

function createReservation(userId, seatId, token) {
  const response = http.post(`${BASE_URL}/api/v1/reservations`, 
    JSON.stringify({
      userId: userId,
      concertId: TEST_CONCERT_ID,
      scheduleId: TEST_SCHEDULE_ID,
      seatId: seatId,
      token: token
    }), 
    {
      headers: { 
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      timeout: '15s'
    }
  );
  
  if (response.status === 200) {
    try {
      const data = JSON.parse(response.body);
      return { reservationId: data.data.reservationId };
    } catch (e) {
      return null;
    }
  }
  return null;
}

function chargeBalance(userId, amount) {
  http.post(`${BASE_URL}/api/v1/balance`, 
    JSON.stringify({
      userId: userId,
      amount: amount.toString() + ".0"
    }), 
    {
      headers: { 
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      timeout: '10s'
    }
  );
}

// 테스트 초기화
export function setup() {
  console.log('🚀 분산락 종합 동시성 테스트 시작!');
  console.log('📊 테스트 대상 API:');
  console.log('   1. 잔액 충전 (단일 락: balance:#userId)');
  console.log('   2. 좌석 예약 (단일 락: seat:#seatId)');
  console.log('   3. 토큰 발급 (단일 락: token:issue:#userId)');
  console.log('   4. 결제 처리 (멀티 락: balance:#userId + reservation:#reservationId) 🎯');
  console.log('   5. 예약 취소 (단일 락: reservation:#reservationId)');
  console.log('');
  console.log('⚡ 스케줄러 최적화: 5초마다 대기열 처리, 30초마다 토큰 정리');
  console.log('🎯 예상 완료 시간: 약 7분');
  
  return { startTime: Date.now() };
}

// 종합 분석
export function teardown(data) {
console.log('\n🏁 분산락 종합 동시성 테스트 완료!');
sleep(2); // 모든 트랜잭션 완료 대기

console.log(`\n═══════════════════════════════════════`);
console.log(`🏆 분산락 동시성 테스트 최종 결과`);
console.log(`⚡ 스케줄러 최적화: 5초 간격 대기열 처리`);
console.log(`═══════════════════════════════════════`);

let totalAttempts = 0;
let totalSuccesses = 0;
let totalConflicts = 0;

// 각 테스트별 상세 결과
Object.entries(testStats).forEach(([testType, stats]) => {
if (stats.attempts > 0) {
const successRate = (stats.successes / stats.attempts * 100).toFixed(1);
const conflictRate = (stats.conflicts / stats.attempts * 100).toFixed(1);

console.log(`\n📊 ${testType.replace(/_/g, ' ').toUpperCase()}:`);
console.log(`   시도: ${stats.attempts}회`);
console.log(`   성공: ${stats.successes}회 (${successRate}%)`);
console.log(`   락 충돌: ${stats.conflicts}회 (${conflictRate}%)`);

totalAttempts += stats.attempts;
totalSuccesses += stats.successes;
  totalConflicts += stats.conflicts;
  }
});

// 전체 통계
const overallSuccessRate = totalAttempts > 0 ? (totalSuccesses / totalAttempts * 100).toFixed(1) : '0';
const overallConflictRate = totalAttempts > 0 ? (totalConflicts / totalAttempts * 100).toFixed(1) : '0';

console.log(`\n🎯 전체 통계:`);
console.log(`   총 시도: ${totalAttempts}회`);
console.log(`   총 성공: ${totalSuccesses}회 (${overallSuccessRate}%)`);
console.log(`   총 락 충돌: ${totalConflicts}회 (${overallConflictRate}%)`);
console.log(`   예약된 좌석: ${reservedSeats.size}개 (${Array.from(reservedSeats).join(', ')})`);

// 성능 등급 - 동시성 테스트에 맞게 수정
let performanceGrade = '';
const successRateNum = parseFloat(overallSuccessRate);
const conflictRateNum = parseFloat(overallConflictRate);

// 동시성 테스트는 락 충돌이 예상되므로 다른 기준 적용
const seatReservationStats = testStats.seat_reservation;
if (seatReservationStats && seatReservationStats.attempts > 0) {
  const expectedConflicts = seatReservationStats.attempts - 1; // 1명만 성공 예상
  const actualConflicts = seatReservationStats.attempts - seatReservationStats.successes;
  
  if (seatReservationStats.successes === 1 && actualConflicts >= expectedConflicts) {
    performanceGrade = '🎆 EXCELLENT - 동시성 제어 완볽 동작';
  } else if (seatReservationStats.successes <= 2 && conflictRateNum >= 60) {
    performanceGrade = '✅ GOOD - 동시성 제어 양호한 성능';
  } else if (successRateNum >= 40) {
    performanceGrade = '⚠️ ACCEPTABLE - 개선 필요';
  } else {
    performanceGrade = '❌ POOR - 분산락 재검토 필요';
  }
} else {
  // 기존 로직 유지
  if (successRateNum >= 80 && conflictRateNum <= 20) {
    performanceGrade = '🌟 EXCELLENT - 분산락 완벽 동작';
  } else if (successRateNum >= 60 && conflictRateNum <= 40) {
    performanceGrade = '✅ GOOD - 분산락 양호한 성능';
  } else if (successRateNum >= 40) {
    performanceGrade = '⚠️ ACCEPTABLE - 개선 필요';
  } else {
    performanceGrade = '❌ POOR - 분산락 재검토 필요';
  }
}

console.log(`\n🏆 최종 성능 등급: ${performanceGrade}`);

console.log(`\n🌆 좌석 예약 동시성 분석 (핵심!):`);
const seatStats = testStats.seat_reservation;
if (seatStats && seatStats.attempts > 0) {
  console.log(`   동시 접근 시도: ${seatStats.attempts}회`);
  console.log(`   예약 성공: ${seatStats.successes}회 (이상적: 1회)`);
  console.log(`   락 충돌/실패: ${seatStats.attempts - seatStats.successes}회`);
  console.log(`   예약된 좌석: ${Array.from(reservedSeats).join(', ') || '없음'}`);
  
  if (seatStats.successes === 1) {
    console.log(`   ✅ 동시성 제어가 정확히 동작하고 있습니다!`);
  } else if (seatStats.successes === 0) {
    console.log(`   ❌ 모든 요청이 실패했습니다. 토큰 또는 서버 문제를 확인하세요.`);
  } else if (seatStats.successes > 1) {
    console.log(`   ⚠️ 예상보다 많은 요청이 성공했습니다. 락 설정을 확인하세요.`);
  }
} else {
  console.log(`   ❌ 좌석 예약 테스트가 실행되지 않았습니다.`);
}
const paymentStats = testStats.payment_process;
if (paymentStats && paymentStats.attempts > 0) {
console.log(`\n🔥 멀티락 성능 분석 (핵심!):`); 
console.log(`   멀티락 시도: ${paymentStats.attempts}회`);
console.log(`   멀티락 성공: ${paymentStats.successes}회`);
console.log(`   멀티락 성공률: ${(paymentStats.successes / paymentStats.attempts * 100).toFixed(1)}%`);
console.log(`   멀티락 충돌률: ${(paymentStats.conflicts / paymentStats.attempts * 100).toFixed(1)}%`);

if (paymentStats.successes / paymentStats.attempts >= 0.7) {
  console.log(`   ✅ 멀티락이 우수한 성능으로 동작하고 있습니다!`);
} else if (paymentStats.successes / paymentStats.attempts >= 0.5) {
  console.log(`   ⚠️ 멀티락 성능이 보통 수준입니다.`);
} else {
  console.log(`   ❌ 멀티락 성능 개선이 필요합니다.`);
  }
}

console.log(`\n💡 분석 및 권장사항:`);
console.log(`   🔧 현재 락 설정: SIMPLE/PUB_SUB 전략`);
console.log(`   ⚡ 최적화된 스케줄러: 5초 간격 처리`);
console.log(`   📈 총 테스트 시간: ${((Date.now() - data.startTime) / 1000 / 60).toFixed(1)}분`);
console.log(`   🎫 예약 성공률: ${reservedSeats.size > 0 ? ((reservedSeats.size / 5) * 100).toFixed(1) : '0'}% (${reservedSeats.size}/5 VU)`);
console.log(`   🎯 멀티락 테스트가 핵심 성능 지표입니다!`);
  
  console.log(`═══════════════════════════════════════\n`);
}

export function handleSummary(data) {
  const totalRequests = data.metrics.http_reqs?.values.count || 0;
  const successfulOps = data.metrics.successful_operations?.values.count || 0;
  const lockConflictsCount = data.metrics.lock_conflicts?.values.count || 0;
  const multiLockSuccessCount = data.metrics.multi_lock_successes?.values.count || 0;
  
  return {
    'distributed-lock-concurrency-results.json': JSON.stringify({
      ...data,
      custom_analysis: {
        total_requests: totalRequests,
        successful_operations: successfulOps,
        lock_conflicts: lockConflictsCount,
        multi_lock_successes: multiLockSuccessCount,
        test_scenarios: 4, // 5에서 4로 수정 (예약 취소 제거)
        apis_tested: [
          'ChargeBalanceUseCase (단일락)',
          'ReserveSeatUseCase (단일락) - 동시성 테스트',
          'TokenIssueUseCase (단일락)',
          'ProcessPaymentUserCase (멀티락)', // 핵심!
        ],
        scheduler_optimization: {
          queue_processing_interval: '5초',
          token_cleanup_interval: '30초',
          estimated_test_duration: '약 7분'
        }
      }
    }, null, 2),
    stdout: `
═══════════════════════════════════════
📊 분산락 종합 동시성 테스트 - K6 통계
⚡ 스케줄러 최적화 완료
═══════════════════════════════════════

🎯 테스트 대상: 분산락 사용 전체 API
   📍 단일 락: 4개 API
   📍 멀티 락: 1개 API (ProcessPaymentUserCase) 🔥
   ⚡ 최적화: 5초 간격 대기열 처리

📈 전체 HTTP 통계:
  🚀 총 요청 수: ${totalRequests}
  ✅ 성공 작업: ${successfulOps}
  🔒 락 충돌: ${lockConflictsCount}
  🎯 멀티락 성공: ${multiLockSuccessCount}

💡 상세 분석은 위의 teardown 로그를 확인하세요!
   특히 멀티락 성능이 핵심 지표입니다.

📁 상세 결과: distributed-lock-concurrency-results.json
═══════════════════════════════════════
    `,
  };
}
