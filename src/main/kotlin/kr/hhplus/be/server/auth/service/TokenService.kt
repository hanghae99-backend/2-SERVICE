package kr.hhplus.be.server.auth.service

import kr.hhplus.be.server.auth.dto.TokenStatusResponse
import kr.hhplus.be.server.auth.entity.TokenStatus
import kr.hhplus.be.server.auth.entity.WaitingToken
import kr.hhplus.be.server.auth.entity.TokenNotFoundException
import kr.hhplus.be.server.auth.entity.TokenActivationException
import kr.hhplus.be.server.auth.factory.TokenFactory
import kr.hhplus.be.server.user.service.UserService
import kr.hhplus.be.server.user.entity.UserNotFoundException
import org.springframework.stereotype.Service

/**
 * 토큰 관련 비즈니스 플로우 조정의 책임을 가진다
 * 각 단계별 전문 컴포넌트들을 조정하여 완전한 비즈니스 플로우를 구성한다
 */
@Service
class TokenService(
    private val userService: UserService,
    private val tokenFactory: TokenFactory,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val queueManager: QueueManager
) {
    
    /**
     * 대기 토큰 발급 플로우 조정
     * 1. 파라미터 검증 -> 2. 사용자 검증 -> 3. 토큰 생성 -> 4. 토큰 저장 -> 5. 대기열 추가
     */
    fun issueWaitingToken(userId: Long): WaitingToken {
        // 1. 사용자 존재 확인
        val user = userService.findUserById(userId)
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다: $userId")
        
        // 2. 토큰 생성
        val waitingToken = tokenFactory.createWaitingToken(userId)
        
        // 3. 토큰 저장
        tokenLifecycleManager.saveToken(waitingToken)
        
        // 4. 대기열에 추가
        queueManager.addToQueue(waitingToken.token)
        
        return waitingToken
    }
    
    /**
     * 토큰 상태 조회 플로우 조정
     * 1. 토큰 존재 확인 -> 2. 상태 조회 -> 3. 사용자 친화적 메시지 변환 -> 4. 대기 순서 조회 (WAITING 상태인 경우)
     */
    fun getTokenStatus(token: String): TokenStatusResponse {
        // 1. 토큰 존재 확인
        tokenLifecycleManager.findToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        
        // 2. 상태 조회
        val status = tokenLifecycleManager.getTokenStatus(token)
        
        // 3. 비즈니스 도메인에 맞는 메시지 변환
        val message = when (status) {
            TokenStatus.WAITING -> "대기 중입니다"
            TokenStatus.ACTIVE -> "예약 가능합니다"
            TokenStatus.EXPIRED -> "토큰이 만료되었습니다"
        }
        
        // 4. 대기 순서 조회 (WAITING 상태인 경우만)
        val queuePosition = if (status == TokenStatus.WAITING) {
            val position = queueManager.getQueuePosition(token)
            if (position >= 0) position + 1 else null // 1부터 시작하도록 +1, 찾지 못하면 null
        } else {
            null
        }
        
        return TokenStatusResponse(status, message, queuePosition)
    }
    
    /**
     * 활성 토큰 검증 플로우 조정
     * 1. 토큰 존재 확인 -> 2. 활성 상태 검증
     */
    fun validateActiveToken(token: String): WaitingToken {
        // 1. 토큰 존재 확인
        val waitingToken = tokenLifecycleManager.findToken(token)
            ?: throw TokenNotFoundException("유효하지 않은 토큰입니다.")
        
        // 2. 비즈니스 규칙: ACTIVE 상태만 허용
        val status = tokenLifecycleManager.getTokenStatus(token)
        if (status != TokenStatus.ACTIVE) {
            throw TokenActivationException("활성화된 토큰이 아닙니다. 현재 상태: $status")
        }
        
        return waitingToken
    }
    
    /**
     * 예약/결제 완료 플로우 조정
     * 1. 토큰 존재 확인 -> 2. 토큰 완료 처리
     */
    fun completeReservation(token: String) {
        // 1. 토큰 존재 확인
        tokenLifecycleManager.findToken(token)
            ?: throw TokenNotFoundException("토큰을 찾을 수 없습니다.")
        // 2. 토큰 완료 처리 (만료 + 다음 사용자 활성화)
        tokenLifecycleManager.completeToken(token)
    }
    
    /**
     * 자동 큐 처리 플로우 조정 (스케줄러용)
     * 1. 만료된 토큰 정리 -> 2. 가용 슬롯 채우기
     */
    fun processQueueAutomatically() {
        // 1. 만료된 활성 토큰들 정리
        tokenLifecycleManager.cleanupExpiredTokens()
        
        // 2. 가용 슬롯을 다음 대기자들로 채우기
        queueManager.processQueueAutomatically()
    }
    
    /**
     * 만료된 토큰 정리 (스케줄러용)
     */
    fun cleanupExpiredActiveTokens() {
        tokenLifecycleManager.cleanupExpiredTokens()
    }
}
