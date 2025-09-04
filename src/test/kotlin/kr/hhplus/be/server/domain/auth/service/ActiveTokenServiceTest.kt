package kr.hhplus.be.server.domain.auth.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate

class ActiveTokenServiceTest : BehaviorSpec({
    
    Given("토큰 활성화 서비스에서") {
        
        When("토큰을 활성화할 때") {
            val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
            val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true)
            val activeTokenService = ActiveTokenService(redisTemplate)
            val token = "test-token"
            
            every { redisTemplate.opsForHash<String, String>() } returns hashOps
            
            activeTokenService.activateToken(token)
            
            Then("Redis에 토큰과 만료시간이 저장되어야 한다") {
                verify { hashOps.put("active_tokens_hash", token, any()) }
            }
        }
        
        When("토큰이 활성화되어 있는지 확인할 때") {
            val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
            val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true)
            val activeTokenService = ActiveTokenService(redisTemplate)
            val token = "active-token"
            
            every { redisTemplate.opsForHash<String, String>() } returns hashOps
            every { hashOps.hasKey("active_tokens_hash", token) } returns true
            
            val result = activeTokenService.isTokenActive(token)
            
            Then("활성화 상태를 확인할 수 있다") {
                result shouldBe true
            }
        }
        
        When("토큰을 완료 처리할 때") {
            val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
            val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true)
            val activeTokenService = ActiveTokenService(redisTemplate)
            val token = "complete-token"
            
            every { redisTemplate.opsForHash<String, String>() } returns hashOps
            
            activeTokenService.completeToken(token)
            
            Then("Redis에서 토큰이 제거되어야 한다") {
                verify { hashOps.delete("active_tokens_hash", token) }
            }
        }
        
        When("만료된 토큰들을 정리할 때") {
            val redisTemplate = mockk<RedisTemplate<String, Any>>(relaxed = true)
            val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true)
            val activeTokenService = ActiveTokenService(redisTemplate)
            
            val expiredTokens = mapOf(
                "expired-token-1" to "1000",
                "expired-token-2" to "2000", 
                "active-token" to "9999999999999"
            )
            
            every { redisTemplate.opsForHash<String, String>() } returns hashOps
            every { hashOps.entries("active_tokens_hash") } returns expiredTokens
            
            activeTokenService.removeExpiredTokens()
            
            Then("만료된 토큰들만 제거되어야 한다") {
                verify { hashOps.delete("active_tokens_hash", "expired-token-1") }
                verify { hashOps.delete("active_tokens_hash", "expired-token-2") }
                verify(exactly = 0) { hashOps.delete("active_tokens_hash", "active-token") }
            }
        }
    }
})
