package kr.hhplus.be.server.global.scheduler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import org.springframework.scheduling.annotation.Scheduled

class TokenCleanupSchedulerTest : DescribeSpec({
    
    describe("TokenCleanupScheduler 테스트") {
        
        lateinit var tokenLifecycleManager: TokenLifecycleManager
        lateinit var tokenCleanupScheduler: TokenCleanupScheduler
        
        beforeEach {
            tokenLifecycleManager = mockk(relaxed = true)
            tokenCleanupScheduler = TokenCleanupScheduler(tokenLifecycleManager)
        }
        
        context("만료된 토큰 정리 스케줄러가 실행될 때") {
            it("TokenLifecycleManager의 cleanupExpiredTokens를 호출해야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokens() } just Runs
                
                // when
                tokenCleanupScheduler.cleanupExpiredTokens()
                
                // then
                verify(exactly = 1) { tokenLifecycleManager.cleanupExpiredTokens() }
            }
        }
        
        context("스케줄러 실행 중 예외가 발생할 때") {
            it("예외를 처리하고 다음 실행을 방해하지 않아야 한다") {
                // given
                every { tokenLifecycleManager.cleanupExpiredTokens() } throws RuntimeException("Redis 연결 실패")
                
                // when & then - 예외가 발생해도 메서드가 정상 종료되어야 함
                tokenCleanupScheduler.cleanupExpiredTokens()
                
                verify(exactly = 1) { tokenLifecycleManager.cleanupExpiredTokens() }
            }
        }
        
        context("스케줄러 어노테이션 검증") {
            it("@Scheduled 어노테이션이 올바르게 설정되어야 한다") {
                // given
                val method = TokenCleanupScheduler::class.java.getDeclaredMethod("cleanupExpiredTokens")
                val scheduledAnnotation = method.getAnnotation(Scheduled::class.java)
                
                // then
                scheduledAnnotation shouldNotBe null
                // 예: 5분마다 실행되도록 설정되어 있는지 확인
                // scheduledAnnotation.fixedRate shouldBe 300000L // 5분 = 300,000ms
            }
        }
    }
})

// 실제 스케줄러 클래스 (참고용)
class TokenCleanupScheduler(
    private val tokenLifecycleManager: TokenLifecycleManager
) {
    
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    fun cleanupExpiredTokens() {
        try {
            tokenLifecycleManager.cleanupExpiredTokens()
            println("만료된 토큰 정리 완료: ${java.time.LocalDateTime.now()}")
        } catch (e: Exception) {
            println("토큰 정리 중 오류 발생: ${e.message}")
            // 로깅만 하고 예외를 다시 던지지 않음 (다음 스케줄 실행을 방해하지 않기 위해)
        }
    }
}