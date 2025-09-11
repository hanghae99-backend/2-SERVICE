import http from 'k6/http';
import { check, sleep } from 'k6';

// 카프카 환경 헬스체크 및 사전 테스트
const BASE_URL = 'http://localhost:8080';
const KAFKA_UI_URL = 'http://localhost:18080';

export let options = {
    vus: 1,
    duration: '30s',
    thresholds: {
        http_req_duration: ['p(95)<3000'],
        http_req_failed: ['rate<0.05'],
    },
};

export function setup() {
    console.log('=== 카프카 환경 헬스체크 시작 ===');
    console.log('테스트 데이터:');
    console.log('- 기존 사용자: 1~10번 (포인트 충전됨)');
    console.log('- 콘서트 스케줄: 1~16번');
    console.log('- 좌석: 각 스케줄당 50개 (가격 10만원)');
    return { startTime: new Date().toISOString() };
}

export default function() {
    // 1. 애플리케이션 서버 상태 확인
    console.log('1. 애플리케이션 서버 헬스체크...');
    const healthResponse = http.get(`${BASE_URL}/actuator/health`);
    
    const appHealthy = check(healthResponse, {
        '애플리케이션 서버 정상': (r) => r.status === 200,
        '헬스체크 응답 정상': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 'UP';
            } catch (e) {
                return false;
            }
        },
    });
    
    if (appHealthy) {
        console.log('✅ 애플리케이션 서버 상태: 정상');
    } else {
        console.log(`❌ 애플리케이션 서버 상태: 비정상 (status: ${healthResponse.status})`);
        return; // 애플리케이션이 비정상이면 테스트 중단
    }
    
    sleep(1);
    
    // 2. 카프카 UI 접근 확인
    console.log('2. 카프카 UI 접근성 확인...');
    const kafkaUIResponse = http.get(`${KAFKA_UI_URL}/api/clusters`);
    
    const kafkaUIHealthy = check(kafkaUIResponse, {
        '카프카 UI 접근 가능': (r) => r.status === 200,
    });
    
    if (kafkaUIHealthy) {
        console.log('✅ 카프카 UI 상태: 접근 가능');
        
        // 클러스터 정보 확인
        try {
            const clusters = JSON.parse(kafkaUIResponse.body);
            if (Array.isArray(clusters) && clusters.length > 0) {
                console.log(`📊 카프카 클러스터 수: ${clusters.length}`);
                clusters.forEach((cluster, index) => {
                    console.log(`   - 클러스터 ${index + 1}: ${cluster.name || 'unknown'}`);
                });
            }
        } catch (e) {
            console.log('⚠️  클러스터 정보 파싱 실패');
        }
    } else {
        console.log(`⚠️  카프카 UI 상태: 접근 불가 (status: ${kafkaUIResponse.status})`);
        console.log('   → 카프카 UI 없이도 테스트 가능하지만, 모니터링이 제한됩니다.');
    }
    
    sleep(1);
    
    // 3. 카프카 토픽 존재 여부 확인 (카프카 UI가 가능한 경우)
    if (kafkaUIHealthy) {
        console.log('3. 카프카 토픽 존재 여부 확인...');
        const topicsResponse = http.get(`${KAFKA_UI_URL}/api/clusters/local/topics`);
        
        const topicsAccessible = check(topicsResponse, {
            '토픽 목록 조회 가능': (r) => r.status === 200,
        });
        
        if (topicsAccessible) {
            try {
                const topics = JSON.parse(topicsResponse.body);
                
                const expectedTopics = ['waiting-token', 'reservation-events', 'payment-events'];
                const existingTopics = topics.map(t => t.name);
                
                console.log(`📋 전체 토픽 수: ${topics.length}`);
                
                expectedTopics.forEach(expectedTopic => {
                    const exists = existingTopics.includes(expectedTopic);
                    const topic = topics.find(t => t.name === expectedTopic);
                    
                    if (exists && topic) {
                        console.log(`✅ ${expectedTopic}: 존재 (파티션: ${topic.partitionCount || 'unknown'})`);
                    } else {
                        console.log(`❌ ${expectedTopic}: 존재하지 않음`);
                    }
                });
                
            } catch (e) {
                console.log(`⚠️  토픽 정보 파싱 실패: ${e.message}`);
            }
        } else {
            console.log(`⚠️  토픽 목록 조회 실패 (status: ${topicsResponse.status})`);
        }
    }
    
    sleep(1);
    
    // 4. 기본 API 동작 확인 (토큰 발급 테스트)
    console.log('4. 기본 API 동작 확인 (토큰 발급 테스트)...');
    const testUserId = 9999; // 테스트용 사용자 ID
    
    const tokenPayload = JSON.stringify({
        userId: testUserId
    });
    
    const tokenParams = {
        headers: { 'Content-Type': 'application/json' },
    };
    
    const tokenResponse = http.post(`${BASE_URL}/api/v1/tokens`, tokenPayload, tokenParams);
    
    const tokenTestSuccess = check(tokenResponse, {
        '토큰 발급 API 정상 동작': (r) => r.status === 201,
        '토큰 응답 형식 정상': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.token !== undefined && body.data.token !== null;
            } catch (e) {
                return false;
            }
        },
    });
    
    if (tokenTestSuccess) {
        try {
            const responseBody = JSON.parse(tokenResponse.body);
            const tokenData = responseBody.data;
            console.log(`✅ 토큰 발급 테스트 성공: userId=${testUserId}, token=${tokenData.token}`);
            console.log('   → 카프카로 토큰 이벤트가 발행되었을 것입니다.');
        } catch (e) {
            console.log(`✅ 토큰 발급 성공했지만 파싱 실패: userId=${testUserId}`);
        }
    } else {
        console.log(`❌ 토큰 발급 테스트 실패: userId=${testUserId}, status=${tokenResponse.status}`);
    }
    
    sleep(2);
    
    // 5. 시스템 메트릭 확인 (선택적)
    console.log('5. 시스템 메트릭 확인...');
    const metricsResponse = http.get(`${BASE_URL}/actuator/metrics`);
    
    const metricsAvailable = check(metricsResponse, {
        '메트릭 엔드포인트 접근 가능': (r) => r.status === 200,
    });
    
    if (metricsAvailable) {
        try {
            const metrics = JSON.parse(metricsResponse.body);
            console.log(`📊 사용 가능한 메트릭 수: ${metrics.names?.length || 0}`);
            
            // 카프카 관련 메트릭 확인
            const kafkaMetrics = metrics.names?.filter(name => 
                name.includes('kafka') || name.includes('spring.kafka')
            ) || [];
            
            if (kafkaMetrics.length > 0) {
                console.log(`   - 카프카 관련 메트릭: ${kafkaMetrics.length}개`);
                kafkaMetrics.slice(0, 5).forEach(metric => {
                    console.log(`     • ${metric}`);
                });
                if (kafkaMetrics.length > 5) {
                    console.log(`     • ... 외 ${kafkaMetrics.length - 5}개`);
                }
            } else {
                console.log('   - 카프카 관련 메트릭: 없음');
            }
            
        } catch (e) {
            console.log(`⚠️  메트릭 정보 파싱 실패: ${e.message}`);
        }
    } else {
        console.log(`⚠️  메트릭 엔드포인트 접근 불가 (status: ${metricsResponse.status})`);
    }
    
    sleep(3); // 다음 반복까지 대기
}

export function teardown(data) {
    console.log('=== 카프카 환경 헬스체크 완료 ===');
    console.log(`시작 시간: ${data.startTime}`);
    console.log(`종료 시간: ${new Date().toISOString()}`);
    console.log('');
    console.log('📋 헬스체크 결과 요약:');
    console.log('1. 애플리케이션 서버 상태');
    console.log('2. 카프카 UI 접근성');  
    console.log('3. 카프카 토픽 존재 여부');
    console.log('4. 기본 API 동작 확인');
    console.log('5. 시스템 메트릭 가용성');
    console.log('');
    console.log('✅ 모든 항목이 정상이면 성능 테스트를 시작할 수 있습니다.');
    console.log('❌ 문제가 있는 항목은 README-kafka-performance.md의 문제해결 섹션을 참조하세요.');
    console.log('==========================================');
}