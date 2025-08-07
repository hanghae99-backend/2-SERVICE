package kr.hhplus.be.server.domain.auth.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.auth.service.TokenDomainService

class TokenDomainServiceTest : DescribeSpec({
    
    val tokenDomainService = TokenDomainService()
    
    describe("validateTokenActivation") {
        context("대기 중인 토큰을 활성화할 때") {
            it("검증이 성공해야 한다") {
                // given
                val waitingToken = mockk<WaitingToken>()
                val currentStatus = TokenStatus.WAITING
                
                // when & then (예외가 발생하지 않아야 함)
                tokenDomainService.validateTokenActivation(waitingToken, currentStatus)
            }
        }
        
        context("대기 중이 아닌 토큰을 활성화할 때") {
            it("TokenActivationException을 던져야 한다") {
                // given
                val waitingToken = mockk<WaitingToken>()
                val currentStatus = TokenStatus.ACTIVE
                
                // when & then
                shouldThrow<TokenActivationException> {
                    tokenDomainService.validateTokenActivation(waitingToken, currentStatus)
                }
            }
        }
    }
    
    describe("validateActiveToken") {
        context("활성화된 토큰을 검증할 때") {
            it("검증이 성공해야 한다") {
                // given
                val waitingToken = mockk<WaitingToken>()
                val currentStatus = TokenStatus.ACTIVE
                
                // when & then (예외가 발생하지 않아야 함)
                tokenDomainService.validateActiveToken(waitingToken, currentStatus)
            }
        }
        
        context("null 토큰을 검증할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val waitingToken = null
                val currentStatus = TokenStatus.ACTIVE
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    tokenDomainService.validateActiveToken(waitingToken, currentStatus)
                }
            }
        }
        
        context("비활성화된 토큰을 검증할 때") {
            it("TokenActivationException을 던져야 한다") {
                // given
                val waitingToken = mockk<WaitingToken>()
                val currentStatus = TokenStatus.WAITING
                
                // when & then
                shouldThrow<TokenActivationException> {
                    tokenDomainService.validateActiveToken(waitingToken, currentStatus)
                }
            }
        }
    }
    
    describe("calculateWaitingTime") {
        context("대기 순서를 기반으로 대기 시간을 계산할 때") {
            it("대기 순서 * 2분으로 계산해야 한다") {
                // given
                val queuePosition = 5
                
                // when
                val waitingTime = tokenDomainService.calculateWaitingTime(queuePosition)
                
                // then
                waitingTime shouldBe 10 // 5 * 2
            }
        }
        
        context("첫 번째 대기자의 경우") {
            it("2분으로 계산해야 한다") {
                // given
                val queuePosition = 1
                
                // when
                val waitingTime = tokenDomainService.calculateWaitingTime(queuePosition)
                
                // then
                waitingTime shouldBe 2 // 1 * 2
            }
        }
    }
    
    describe("calculateEstimatedWaitingTime") {
        context("양수 대기 순서로 예상 대기 시간을 계산할 때") {
            it("적절한 예상 시간을 반환해야 한다") {
                // given
                val queuePosition = 25
                
                // when
                val estimatedTime = tokenDomainService.calculateEstimatedWaitingTime(queuePosition)
                
                // then
                estimatedTime shouldBe 2 // (25 + 1) / 10 = 2.6 -> 2
            }
        }
        
        context("음수 대기 순서로 예상 대기 시간을 계산할 때") {
            it("null을 반환해야 한다") {
                // given
                val queuePosition = -1
                
                // when
                val estimatedTime = tokenDomainService.calculateEstimatedWaitingTime(queuePosition)
                
                // then
                estimatedTime shouldBe null
            }
        }
        
        context("소수의 대기자가 있을 때") {
            it("최소 1분을 반환해야 한다") {
                // given
                val queuePosition = 3
                
                // when
                val estimatedTime = tokenDomainService.calculateEstimatedWaitingTime(queuePosition)
                
                // then
                estimatedTime shouldBe 1 // (3 + 1) / 10 = 0.4 -> 최소 1
            }
        }
    }
    
    describe("calculateAvailableSlots") {
        context("현재 활성 토큰 수를 기반으로 가용 슬롯을 계산할 때") {
            it("최대 활성 토큰 수에서 현재 수를 뺀 값을 반환해야 한다") {
                // given
                val currentActiveCount = 70L
                
                // when
                val availableSlots = tokenDomainService.calculateAvailableSlots(currentActiveCount)
                
                // then
                availableSlots shouldBe 30 // 100 - 70
            }
        }
        
        context("최대 활성 토큰 수에 도달했을 때") {
            it("0을 반환해야 한다") {
                // given
                val currentActiveCount = 100L
                
                // when
                val availableSlots = tokenDomainService.calculateAvailableSlots(currentActiveCount)
                
                // then
                availableSlots shouldBe 0 // 100 - 100
            }
        }
        
        context("최대 활성 토큰 수를 초과했을 때") {
            it("음수를 반환해야 한다") {
                // given
                val currentActiveCount = 110L
                
                // when
                val availableSlots = tokenDomainService.calculateAvailableSlots(currentActiveCount)
                
                // then
                availableSlots shouldBe -10 // 100 - 110
            }
        }
    }
    
    describe("getStatusMessage") {
        context("WAITING 상태일 때") {
            it("대기 중 메시지를 반환해야 한다") {
                // when
                val message = tokenDomainService.getStatusMessage(TokenStatus.WAITING)
                
                // then
                message shouldBe "대기 중입니다"
            }
        }
        
        context("ACTIVE 상태일 때") {
            it("서비스 이용 가능 메시지를 반환해야 한다") {
                // when
                val message = tokenDomainService.getStatusMessage(TokenStatus.ACTIVE)
                
                // then
                message shouldBe "서비스 이용 가능합니다"
            }
        }
        
        context("EXPIRED 상태일 때") {
            it("만료 메시지를 반환해야 한다") {
                // when
                val message = tokenDomainService.getStatusMessage(TokenStatus.EXPIRED)
                
                // then
                message shouldBe "토큰이 만료되었습니다"
            }
        }
    }
    
    describe("getQueueStatusMessage") {
        context("WAITING 상태일 때") {
            it("대기 중 메시지를 반환해야 한다") {
                // given
                val queuePosition = 5
                
                // when
                val message = tokenDomainService.getQueueStatusMessage(TokenStatus.WAITING, queuePosition)
                
                // then
                message shouldBe "대기 중입니다"
            }
        }
        
        context("ACTIVE 상태일 때") {
            it("서비스 이용 가능 메시지를 반환해야 한다") {
                // given
                val queuePosition = 0
                
                // when
                val message = tokenDomainService.getQueueStatusMessage(TokenStatus.ACTIVE, queuePosition)
                
                // then
                message shouldBe "서비스 이용 가능합니다"
            }
        }
        
        context("EXPIRED 상태일 때") {
            it("만료 메시지를 반환해야 한다") {
                // given
                val queuePosition = -1
                
                // when
                val message = tokenDomainService.getQueueStatusMessage(TokenStatus.EXPIRED, queuePosition)
                
                // then
                message shouldBe "토큰이 만료되었습니다"
            }
        }
    }
})
