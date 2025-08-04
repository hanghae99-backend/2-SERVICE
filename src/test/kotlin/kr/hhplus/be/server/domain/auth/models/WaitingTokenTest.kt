package kr.hhplus.be.server.domain.auth.models

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.global.exception.ParameterValidationException

class WaitingTokenTest : DescribeSpec({
    
    describe("WaitingToken.create") {
        context("유효한 파라미터로 토큰을 생성할 때") {
            it("정상적으로 WaitingToken을 생성해야 한다") {
                // given
                val token = "valid-token-123"
                val userId = 456L
                
                // when
                val waitingToken = WaitingToken.create(token, userId)
                
                // then
                waitingToken.token shouldBe token
                waitingToken.userId shouldBe userId
            }
            
            it("생성된 토큰이 유효한 상태여야 한다") {
                // given
                val token = "another-valid-token"
                val userId = 789L
                
                // when
                val waitingToken = WaitingToken.create(token, userId)
                
                // then
                waitingToken.isValidToken() shouldBe true
                waitingToken.belongsToUser(userId) shouldBe true
            }
        }
        
        context("잘못된 토큰으로 생성할 때") {
            it("빈 토큰으로 생성 시 ParameterValidationException을 던져야 한다") {
                // given
                val emptyToken = ""
                val userId = 123L
                
                // when & then
                shouldThrow<ParameterValidationException> {
                    WaitingToken.create(emptyToken, userId)
                }
            }
            
            it("공백만 있는 토큰으로 생성 시 ParameterValidationException을 던져야 한다") {
                // given
                val blankToken = "   "
                val userId = 123L
                
                // when & then
                shouldThrow<ParameterValidationException> {
                    WaitingToken.create(blankToken, userId)
                }
            }
        }
        
        context("잘못된 사용자 ID로 생성할 때") {
            it("0으로 생성 시 ParameterValidationException을 던져야 한다") {
                // given
                val token = "valid-token"
                val invalidUserId = 0L
                
                // when & then
                val exception = shouldThrow<ParameterValidationException> {
                    WaitingToken.create(token, invalidUserId)
                }
                exception.message shouldBe "사용자 ID는 0보다 커야 합니다: $invalidUserId"
            }
            
            it("음수로 생성 시 ParameterValidationException을 던져야 한다") {
                // given
                val token = "valid-token"
                val invalidUserId = -123L
                
                // when & then
                val exception = shouldThrow<ParameterValidationException> {
                    WaitingToken.create(token, invalidUserId)
                }
                exception.message shouldBe "사용자 ID는 0보다 커야 합니다: $invalidUserId"
            }
        }
        
        context("경계값으로 생성할 때") {
            it("사용자 ID 1로 정상 생성되어야 한다") {
                // given
                val token = "valid-token"
                val userId = 1L
                
                // when
                val waitingToken = WaitingToken.create(token, userId)
                
                // then
                waitingToken.token shouldBe token
                waitingToken.userId shouldBe userId
                waitingToken.isValidToken() shouldBe true
            }
            
            it("Long.MAX_VALUE 사용자 ID로 정상 생성되어야 한다") {
                // given
                val token = "valid-token"
                val userId = Long.MAX_VALUE
                
                // when
                val waitingToken = WaitingToken.create(token, userId)
                
                // then
                waitingToken.token shouldBe token
                waitingToken.userId shouldBe userId
                waitingToken.isValidToken() shouldBe true
            }
        }
    }
    
    describe("isValidToken") {
        context("유효한 토큰을 검증할 때") {
            it("정상적인 토큰이면 true를 반환해야 한다") {
                // given
                val waitingToken = WaitingToken("valid-token-abc123", 456L)
                
                // when
                val result = waitingToken.isValidToken()
                
                // then
                result shouldBe true
            }
            
            it("특수문자가 포함된 토큰도 유효해야 한다") {
                // given
                val waitingToken = WaitingToken("token-with-special-chars_123!@#", 789L)
                
                // when
                val result = waitingToken.isValidToken()
                
                // then
                result shouldBe true
            }
            
            it("UUID 형식의 토큰이 유효해야 한다") {
                // given
                val waitingToken = WaitingToken("550e8400-e29b-41d4-a716-446655440000", 123L)
                
                // when
                val result = waitingToken.isValidToken()
                
                // then
                result shouldBe true
            }
        }
        
        context("유효하지 않은 토큰을 검증할 때") {
            it("빈 토큰이면 false를 반환해야 한다") {
                // given
                val waitingToken = WaitingToken("", 123L)
                
                // when
                val result = waitingToken.isValidToken()
                
                // then
                result shouldBe false
            }
            
            it("공백만 있는 토큰이면 false를 반환해야 한다") {
                // given
                val waitingToken = WaitingToken("   ", 123L)
                
                // when
                val result = waitingToken.isValidToken()
                
                // then
                result shouldBe false
            }
            
            it("탭과 개행문자만 있는 토큰이면 false를 반환해야 한다") {
                // given
                val waitingToken = WaitingToken("\t\n\r", 123L)
                
                // when
                val result = waitingToken.isValidToken()
                
                // then
                result shouldBe false
            }
        }
    }
    
    describe("belongsToUser") {
        context("토큰 소유자를 검증할 때") {
            it("같은 사용자 ID면 true를 반환해야 한다") {
                // given
                val userId = 123L
                val waitingToken = WaitingToken("test-token", userId)
                
                // when
                val result = waitingToken.belongsToUser(userId)
                
                // then
                result shouldBe true
            }
            
            it("다른 사용자 ID면 false를 반환해야 한다") {
                // given
                val userId = 123L
                val otherUserId = 456L
                val waitingToken = WaitingToken("test-token", userId)
                
                // when
                val result = waitingToken.belongsToUser(otherUserId)
                
                // then
                result shouldBe false
            }
            
            it("0과 비교하면 false를 반환해야 한다") {
                // given
                val userId = 123L
                val waitingToken = WaitingToken("test-token", userId)
                
                // when
                val result = waitingToken.belongsToUser(0L)
                
                // then
                result shouldBe false
            }
            
            it("음수와 비교하면 false를 반환해야 한다") {
                // given
                val userId = 123L
                val waitingToken = WaitingToken("test-token", userId)
                
                // when
                val result = waitingToken.belongsToUser(-456L)
                
                // then
                result shouldBe false
            }
        }
        
        context("경계값으로 소유자를 검증할 때") {
            it("Long.MAX_VALUE 사용자 ID도 정확히 비교해야 한다") {
                // given
                val userId = Long.MAX_VALUE
                val waitingToken = WaitingToken("test-token", userId)
                
                // when
                val resultSame = waitingToken.belongsToUser(userId)
                val resultDifferent = waitingToken.belongsToUser(userId - 1)
                
                // then
                resultSame shouldBe true
                resultDifferent shouldBe false
            }
            
            it("최소값 사용자 ID(1)도 정확히 비교해야 한다") {
                // given
                val userId = 1L
                val waitingToken = WaitingToken("test-token", userId)
                
                // when
                val resultSame = waitingToken.belongsToUser(userId)
                val resultDifferent = waitingToken.belongsToUser(userId + 1)
                
                // then
                resultSame shouldBe true
                resultDifferent shouldBe false
            }
        }
    }
    
    describe("WaitingToken 동등성") {
        context("두 WaitingToken을 비교할 때") {
            it("같은 토큰과 사용자 ID를 가지면 동등해야 한다") {
                // given
                val token = "same-token"
                val userId = 123L
                val waitingToken1 = WaitingToken(token, userId)
                val waitingToken2 = WaitingToken(token, userId)
                
                // when & then
                waitingToken1 shouldBe waitingToken2
                waitingToken1.hashCode() shouldBe waitingToken2.hashCode()
            }
            
            it("다른 토큰을 가지면 동등하지 않아야 한다") {
                // given
                val userId = 123L
                val waitingToken1 = WaitingToken("token1", userId)
                val waitingToken2 = WaitingToken("token2", userId)
                
                // when & then
                waitingToken1 shouldBe waitingToken1
                (waitingToken1 == waitingToken2) shouldBe false
            }
            
            it("다른 사용자 ID를 가지면 동등하지 않아야 한다") {
                // given
                val token = "same-token"
                val waitingToken1 = WaitingToken(token, 123L)
                val waitingToken2 = WaitingToken(token, 456L)
                
                // when & then
                waitingToken1 shouldBe waitingToken1
                (waitingToken1 == waitingToken2) shouldBe false
            }
        }
    }
})