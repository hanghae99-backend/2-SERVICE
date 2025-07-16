package kr.hhplus.be.server.auth.factory

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

class TokenFactoryUnitTest : BehaviorSpec({
    lateinit var tokenFactory: TokenFactory

    beforeTest {
        tokenFactory = TokenFactory()
    }

    given("TokenFactory는 토큰 생성의 단일 책임을 가진다") {
        `when`("토큰 생성을 요청받으면") {
            then("고유한 UUID 기반 토큰을 생성한다") {
                // when
                val token = tokenFactory.createToken()

                // then - TokenFactory의 책임: 고유 토큰 생성
                token.shouldNotBeBlank()
                token.length shouldBe 36 // UUID 형식
            }
        }

        `when`("사용자 ID와 함께 대기 토큰 생성을 요청받으면") {
            then("사용자 정보가 포함된 대기 토큰을 생성한다") {
                // given
                val userId = 123L

                // when
                val waitingToken = tokenFactory.createWaitingToken(userId)

                // then - TokenFactory의 책임: 완전한 대기 토큰 객체 생성
                waitingToken.token.shouldNotBeBlank()
                waitingToken.userId shouldBe userId
                waitingToken.token.length shouldBe 36
            }
        }

        `when`("여러 번 토큰 생성을 요청받으면") {
            then("매번 다른 고유한 토큰을 생성한다") {
                // when
                val token1 = tokenFactory.createToken()
                val token2 = tokenFactory.createToken()
                val token3 = tokenFactory.createToken()

                // then - TokenFactory의 책임: 고유성 보장
                token1 shouldBe token1 // 자기 자신과는 같음
                token1.shouldNotBe(token2) // 서로 다름
                token2.shouldNotBe(token3)
                token1.shouldNotBe(token3)
            }
        }
    }
})