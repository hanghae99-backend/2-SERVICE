package kr.hhplus.be.server.domain.auth.exception

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

class AuthExceptionsTest : DescribeSpec({
    
    describe("TokenNotFoundException") {
        context("기본값으로 예외를 생성할 때") {
            it("기본 메시지와 오류 코드가 설정되어야 한다") {
                // when
                val exception = TokenNotFoundException()
                
                // then
                exception.message shouldBe "토큰을 찾을 수 없습니다"
                exception.errorCode shouldBe "TOKEN_NOT_FOUND"
                exception.status shouldBe HttpStatus.NOT_FOUND
            }
        }
        
        context("커스텀 메시지로 예외를 생성할 때") {
            it("지정한 메시지가 설정되어야 한다") {
                // given
                val customMessage = "지정된 토큰을 찾을 수 없습니다: test-token"
                
                // when
                val exception = TokenNotFoundException(customMessage)
                
                // then
                exception.message shouldBe customMessage
                exception.errorCode shouldBe "TOKEN_NOT_FOUND"
                exception.status shouldBe HttpStatus.NOT_FOUND
            }
        }
        
        context("모든 파라미터를 지정하여 예외를 생성할 때") {
            it("모든 값이 올바르게 설정되어야 한다") {
                // given
                val customMessage = "사용자 정의 메시지"
                val customErrorCode = "CUSTOM_ERROR"
                val customStatus = HttpStatus.BAD_REQUEST
                
                // when
                val exception = TokenNotFoundException(customMessage, customErrorCode, customStatus)
                
                // then
                exception.message shouldBe customMessage
                exception.errorCode shouldBe customErrorCode
                exception.status shouldBe customStatus
            }
        }
    }
    
    describe("TokenExpiredException") {
        context("기본값으로 예외를 생성할 때") {
            it("기본 메시지와 오류 코드가 설정되어야 한다") {
                // when
                val exception = TokenExpiredException()
                
                // then
                exception.message shouldBe "토큰이 만료되었습니다"
                exception.errorCode shouldBe "TOKEN_EXPIRED"
                exception.status shouldBe HttpStatus.UNAUTHORIZED
            }
        }
        
        context("커스텀 메시지로 예외를 생성할 때") {
            it("지정한 메시지가 설정되어야 한다") {
                // given
                val customMessage = "토큰이 10분 전에 만료되었습니다"
                
                // when
                val exception = TokenExpiredException(customMessage)
                
                // then
                exception.message shouldBe customMessage
                exception.errorCode shouldBe "TOKEN_EXPIRED"
                exception.status shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }
    
    describe("InvalidTokenException") {
        context("기본값으로 예외를 생성할 때") {
            it("기본 메시지와 오류 코드가 설정되어야 한다") {
                // when
                val exception = InvalidTokenException()
                
                // then
                exception.message shouldBe "유효하지 않은 토큰입니다"
                exception.errorCode shouldBe "INVALID_TOKEN"
                exception.status shouldBe HttpStatus.UNAUTHORIZED
            }
        }
        
        context("커스텀 메시지로 예외를 생성할 때") {
            it("지정한 메시지가 설정되어야 한다") {
                // given
                val customMessage = "토큰 형식이 올바르지 않습니다"
                
                // when
                val exception = InvalidTokenException(customMessage)
                
                // then
                exception.message shouldBe customMessage
                exception.errorCode shouldBe "INVALID_TOKEN"
                exception.status shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }
    
    describe("TokenIssuanceException") {
        context("기본값으로 예외를 생성할 때") {
            it("기본 메시지와 오류 코드가 설정되어야 한다") {
                // when
                val exception = TokenIssuanceException()
                
                // then
                exception.message shouldBe "토큰 발급에 실패했습니다"
                exception.errorCode shouldBe "TOKEN_ISSUANCE_FAILED"
                exception.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }
        }
        
        context("커스텀 메시지로 예외를 생성할 때") {
            it("지정한 메시지가 설정되어야 한다") {
                // given
                val customMessage = "Redis 연결 실패로 인한 토큰 발급 실패"
                
                // when
                val exception = TokenIssuanceException(customMessage)
                
                // then
                exception.message shouldBe customMessage
                exception.errorCode shouldBe "TOKEN_ISSUANCE_FAILED"
                exception.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }
        }
    }
    
    describe("QueueFullException") {
        context("기본값으로 예외를 생성할 때") {
            it("기본 메시지와 오류 코드가 설정되어야 한다") {
                // when
                val exception = QueueFullException()
                
                // then
                exception.message shouldBe "대기열이 가득 찼습니다"
                exception.errorCode shouldBe "QUEUE_FULL"
                exception.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
            }
        }
        
        context("커스텀 메시지로 예외를 생성할 때") {
            it("지정한 메시지가 설정되어야 한다") {
                // given
                val customMessage = "현재 대기열에 10,000명이 대기 중입니다"
                
                // when
                val exception = QueueFullException(customMessage)
                
                // then
                exception.message shouldBe customMessage
                exception.errorCode shouldBe "QUEUE_FULL"
                exception.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
            }
        }
    }
    
    describe("TokenActivationException") {
        context("기본값으로 예외를 생성할 때") {
            it("기본 메시지와 오류 코드가 설정되어야 한다") {
                // when
                val exception = TokenActivationException()
                
                // then
                exception.message shouldBe "토큰이 만료되었습니다"
                exception.errorCode shouldBe "TOKEN_EXPIRED"
                exception.status shouldBe HttpStatus.UNAUTHORIZED
            }
        }
        
        context("커스텀 메시지로 예외를 생성할 때") {
            it("지정한 메시지가 설정되어야 한다") {
                // given
                val customMessage = "토큰이 활성화되지 않았습니다"
                
                // when
                val exception = TokenActivationException(customMessage)
                
                // then
                exception.message shouldBe customMessage
                exception.errorCode shouldBe "TOKEN_EXPIRED"
                exception.status shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }
    
    describe("AuthErrorCode") {
        context("TokenNotFound 오류 코드") {
            it("올바른 값들을 가져야 한다") {
                // when
                val errorCode = AuthErrorCode.TokenNotFound
                
                // then
                errorCode.code shouldBe "TOKEN_NOT_FOUND"
                errorCode.defaultMessage shouldBe "토큰을 찾을 수 없습니다"
                errorCode.httpStatus shouldBe HttpStatus.NOT_FOUND
            }
        }
        
        context("TokenExpired 오류 코드") {
            it("올바른 값들을 가져야 한다") {
                // when
                val errorCode = AuthErrorCode.TokenExpired
                
                // then
                errorCode.code shouldBe "TOKEN_EXPIRED"
                errorCode.defaultMessage shouldBe "토큰이 만료되었습니다"
                errorCode.httpStatus shouldBe HttpStatus.UNAUTHORIZED
            }
        }
        
        context("InvalidToken 오류 코드") {
            it("올바른 값들을 가져야 한다") {
                // when
                val errorCode = AuthErrorCode.InvalidToken
                
                // then
                errorCode.code shouldBe "INVALID_TOKEN"
                errorCode.defaultMessage shouldBe "유효하지 않은 토큰입니다"
                errorCode.httpStatus shouldBe HttpStatus.UNAUTHORIZED
            }
        }
        
        context("TokenIssuanceFailed 오류 코드") {
            it("올바른 값들을 가져야 한다") {
                // when
                val errorCode = AuthErrorCode.TokenIssuanceFailed
                
                // then
                errorCode.code shouldBe "TOKEN_ISSUANCE_FAILED"
                errorCode.defaultMessage shouldBe "토큰 발급에 실패했습니다"
                errorCode.httpStatus shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }
        }
        
        context("QueueFull 오류 코드") {
            it("올바른 값들을 가져야 한다") {
                // when
                val errorCode = AuthErrorCode.QueueFull
                
                // then
                errorCode.code shouldBe "QUEUE_FULL"
                errorCode.defaultMessage shouldBe "대기열이 가득 찼습니다"
                errorCode.httpStatus shouldBe HttpStatus.SERVICE_UNAVAILABLE
            }
        }
    }
    
    describe("예외 상속 관계") {
        context("모든 Auth 예외들") {
            it("RuntimeException을 상속해야 한다") {
                // when
                val tokenNotFound = TokenNotFoundException()
                val tokenExpired = TokenExpiredException()
                val invalidToken = InvalidTokenException()
                val tokenIssuance = TokenIssuanceException()
                val queueFull = QueueFullException()
                val tokenActivation = TokenActivationException()
                
                // then
                (tokenNotFound is RuntimeException) shouldBe true
                (tokenExpired is RuntimeException) shouldBe true
                (invalidToken is RuntimeException) shouldBe true
                (tokenIssuance is RuntimeException) shouldBe true
                (queueFull is RuntimeException) shouldBe true
                (tokenActivation is RuntimeException) shouldBe true
            }
        }
    }
})