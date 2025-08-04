package kr.hhplus.be.server.domain.auth.factory

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import kr.hhplus.be.server.domain.auth.models.WaitingToken

class TokenFactoryTest : DescribeSpec({
    
    val tokenFactory = TokenFactory()
    
    describe("createToken") {
        context("토큰을 생성할 때") {
            it("UUID 형식의 토큰을 생성해야 한다") {
                // when
                val token = tokenFactory.createToken()
                
                // then
                token shouldNotBe null
                token.length shouldBe 36 // UUID는 36자리
                // UUID 패턴 검증: 8-4-4-4-12 형식
                token shouldMatch Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
            }
            
            it("호출할 때마다 다른 토큰을 생성해야 한다") {
                // when
                val token1 = tokenFactory.createToken()
                val token2 = tokenFactory.createToken()
                val token3 = tokenFactory.createToken()
                
                // then
                token1 shouldNotBe token2
                token2 shouldNotBe token3
                token1 shouldNotBe token3
            }
            
            it("1000번 호출해도 중복이 발생하지 않아야 한다") {
                // when
                val tokens = (1..1000).map { tokenFactory.createToken() }.toSet()
                
                // then
                tokens.size shouldBe 1000 // 모든 토큰이 유니크해야 함
            }
        }
    }
    
    describe("createWaitingToken") {
        context("유효한 사용자 ID로 대기 토큰을 생성할 때") {
            it("유효한 WaitingToken을 생성해야 한다") {
                // given
                val userId = 123L
                
                // when
                val waitingToken = tokenFactory.createWaitingToken(userId)
                
                // then
                waitingToken shouldNotBe null
                waitingToken.userId shouldBe userId
                waitingToken.token shouldNotBe null
                waitingToken.token.length shouldBe 36
                waitingToken.token shouldMatch Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
            }
            
            it("생성된 토큰이 유효한 상태여야 한다") {
                // given
                val userId = 456L
                
                // when
                val waitingToken = tokenFactory.createWaitingToken(userId)
                
                // then
                waitingToken.isValidToken() shouldBe true
                waitingToken.belongsToUser(userId) shouldBe true
                waitingToken.belongsToUser(999L) shouldBe false
            }
            
            it("같은 사용자 ID로도 다른 토큰을 생성해야 한다") {
                // given
                val userId = 789L
                
                // when
                val token1 = tokenFactory.createWaitingToken(userId)
                val token2 = tokenFactory.createWaitingToken(userId)
                
                // then
                token1.token shouldNotBe token2.token
                token1.userId shouldBe token2.userId
            }
        }
        
        context("경계값 사용자 ID로 대기 토큰을 생성할 때") {
            it("최소값 사용자 ID(1)로 토큰을 생성할 수 있어야 한다") {
                // given
                val userId = 1L
                
                // when
                val waitingToken = tokenFactory.createWaitingToken(userId)
                
                // then
                waitingToken shouldNotBe null
                waitingToken.userId shouldBe userId
                waitingToken.isValidToken() shouldBe true
            }
            
            it("큰 값의 사용자 ID로도 토큰을 생성할 수 있어야 한다") {
                // given
                val userId = Long.MAX_VALUE
                
                // when
                val waitingToken = tokenFactory.createWaitingToken(userId)
                
                // then
                waitingToken shouldNotBe null
                waitingToken.userId shouldBe userId
                waitingToken.isValidToken() shouldBe true
            }
        }
        
        context("여러 토큰을 동시에 생성할 때") {
            it("모든 토큰이 유니크해야 한다") {
                // given
                val userId = 100L
                val tokenCount = 100
                
                // when
                val tokens = (1..tokenCount).map { 
                    tokenFactory.createWaitingToken(userId).token 
                }.toSet()
                
                // then
                tokens.size shouldBe tokenCount // 모든 토큰이 유니크해야 함
            }
        }
    }
})