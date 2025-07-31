package kr.hhplus.be.server.domain.auth.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import kr.hhplus.be.server.domain.auth.infrastructure.RedisTokenStore
import kr.hhplus.be.server.domain.auth.service.*
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.model.User
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Auth 도메인 통합 테스트
 * - 토큰 발급/관리 전체 플로우 검증
 * - Redis 기반 토큰 스토어 통합 동작 검증
 * - 대기열 관리 및 동시성 처리 검증
 * - 토큰 생명주기 관리 검증
 */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
@ActiveProfiles("test")
class AuthDomainIntegrationTest(
    private val tokenService: TokenService,
    private val queueManager: QueueManager,
    private val tokenLifecycleManager: TokenLifecycleManager,
    private val tokenDomainService: TokenDomainService,
    private val redisTokenStore: RedisTokenStore,
    private val userJpaRepository: UserJpaRepository
) : BehaviorSpec({

    Given("토큰 발급 시나리오에서") {
        val userId = 1000L
        val user = User.create(userId)
        userJpaRepository.save(user)

        When("정상적인 대기 토큰 발급을 요청할 때") {
            val tokenDetail = tokenService.issueWaitingToken(userId)

            Then("토큰이 정상적으로 발급되어야 한다") {
                tokenDetail shouldNotBe null
                tokenDetail.userId shouldBe userId
                tokenDetail.status shouldBe "WAITING"
                tokenDetail.queuePosition shouldNotBe null
                tokenDetail.estimatedWaitingTime shouldNotBe null
                
                // Redis에 저장 확인
                val storedToken = redisTokenStore.findByToken(tokenDetail.token)
                storedToken shouldNotBe null
                storedToken!!.userId shouldBe userId
            }
        }

        When("동일한 사용자가 중복 대기 토큰 발급을 요청할 때") {
            val firstTokenDetail = tokenService.issueWaitingToken(userId)
            val secondTokenDetail = tokenService.issueWaitingToken(userId)

            Then("기존 토큰이 무효화되고 새 토큰이 발급되어야 한다") {
                secondTokenDetail shouldNotBe null
                secondTokenDetail.token shouldNotBe firstTokenDetail.token
                
                // 기존 토큰 무효화 확인
                val oldToken = redisTokenStore.findByToken(firstTokenDetail.token)
                oldToken shouldBe null
                
                // 새 토큰 유효성 확인
                val newToken = redisTokenStore.findByToken(secondTokenDetail.token)
                newToken shouldNotBe null
            }
        }
    }

    Given("대기열 관리 시나리오에서") {
        When("여러 사용자가 동시에 대기열에 진입할 때") {
            val userCount = 30
            val executor = Executors.newFixedThreadPool(15)
            val successCount = AtomicInteger(0)
            val queuePositions = mutableListOf<Int>()
            
            // 사용자들 미리 생성
            val userIds = (2000L..2000L + userCount).toList()
            userIds.forEach { userId ->
                val user = User.create(userId)
                userJpaRepository.save(user)
            }
            
            val futures = userIds.map { userId ->
                CompletableFuture.supplyAsync({
                    try {
                        val tokenDetail = tokenService.issueWaitingToken(userId)
                        val position = tokenDetail.queuePosition!!
                        synchronized(queuePositions) {
                            queuePositions.add(position)
                        }
                        successCount.incrementAndGet()
                        position
                    } catch (e: Exception) {
                        -1
                    }
                }, executor)
            }
            
            val results = CompletableFuture.allOf(*futures.toTypedArray())
                .thenApply { futures.map { it.get() } }
                .get(30, TimeUnit.SECONDS)

            Then("모든 사용자가 대기열에 순서대로 배치되어야 한다") {
                successCount.get() shouldBe userCount
                queuePositions.size shouldBe userCount
                
                // 대기 순서가 중복되지 않아야 함
                val uniquePositions = queuePositions.toSet()
                uniquePositions.size shouldBe userCount
                
                // 대기 순서가 1부터 시작해야 함
                queuePositions.min() shouldBe 1
                queuePositions.max() shouldBe userCount
            }
        }

        When("대기열에서 사용자들을 순차적으로 활성화할 때") {
            val waitingUsers = 15
            val activationCount = 5
            
            // 대기 중인 사용자들 생성
            val waitingTokens = (3000L..3000L + waitingUsers).map { userId ->
                val user = User.create(userId)
                userJpaRepository.save(user)
                tokenService.issueWaitingToken(userId)
            }
            
            // 일부 사용자 활성화
            repeat(activationCount) {
                queueManager.processQueueAutomatically()
            }

            Then("지정된 수만큼 사용자가 활성화되어야 한다") {
                var activeCount = 0
                waitingTokens.forEach { tokenDetail ->
                    val currentToken = redisTokenStore.findByToken(tokenDetail.token)
                    if (currentToken != null) {
                        val status = redisTokenStore.getTokenStatus(tokenDetail.token)
                        if (status == TokenStatus.ACTIVE) {
                            activeCount++
                        }
                    }
                }
                // 활성화 로직에 따라 실제 활성화된 수 확인
                activeCount shouldBe activationCount.coerceAtMost(100) // MAX_ACTIVE_TOKENS 제한
            }
        }
    }

    Given("토큰 생명주기 관리 시나리오에서") {
        val userId = 4000L
        val user = User.create(userId)
        userJpaRepository.save(user)

        When("토큰을 발급하고 만료 처리할 때") {
            val tokenDetail = tokenService.issueWaitingToken(userId)
            
            // 토큰 만료 처리
            tokenLifecycleManager.expireToken(tokenDetail.token)

            Then("토큰이 만료 상태가 되어야 한다") {
                val status = redisTokenStore.getTokenStatus(tokenDetail.token)
                status shouldBe TokenStatus.EXPIRED
            }
        }

        When("활성 토큰으로 검증을 수행할 때") {
            val tokenDetail = tokenService.issueWaitingToken(userId)
            
            // 토큰을 활성화
            queueManager.activateToken(tokenDetail.token)
            
            val validatedToken = tokenService.validateActiveToken(tokenDetail.token)

            Then("토큰 검증이 성공해야 한다") {
                validatedToken shouldNotBe null
                validatedToken.userId shouldBe userId
                
                val status = redisTokenStore.getTokenStatus(tokenDetail.token)
                status shouldBe TokenStatus.ACTIVE
            }
        }

        When("만료된 토큰으로 검증을 수행할 때") {
            val tokenDetail = tokenService.issueWaitingToken(userId + 1)
            
            // 토큰 만료
            tokenLifecycleManager.expireToken(tokenDetail.token)

            Then("토큰 검증이 실패해야 한다") {
                shouldThrow<Exception> {
                    tokenService.validateActiveToken(tokenDetail.token)
                }
            }
        }
    }

    Given("토큰 상태 조회 시나리오에서") {
        val userId = 5000L
        val user = User.create(userId)
        userJpaRepository.save(user)

        When("대기 중인 토큰의 상태를 조회할 때") {
            val tokenDetail = tokenService.issueWaitingToken(userId)
            val queueDetail = tokenService.getTokenQueueStatus(tokenDetail.token)

            Then("대기열 정보가 정확히 반환되어야 한다") {
                queueDetail shouldNotBe null
                queueDetail.status shouldBe "WAITING"
                queueDetail.queuePosition shouldNotBe null
                queueDetail.estimatedWaitingTime shouldNotBe null
                queueDetail.message shouldBe "대기 중입니다"
            }
        }

        When("활성화된 토큰의 상태를 조회할 때") {
            val tokenDetail = tokenService.issueWaitingToken(userId + 1)
            queueManager.activateToken(tokenDetail.token)
            
            val queueDetail = tokenService.getTokenQueueStatus(tokenDetail.token)

            Then("활성 상태 정보가 반환되어야 한다") {
                queueDetail shouldNotBe null
                queueDetail.status shouldBe "ACTIVE"
                queueDetail.queuePosition shouldBe null
                queueDetail.estimatedWaitingTime shouldBe null
                queueDetail.message shouldBe "서비스 이용 가능합니다"
            }
        }

        When("존재하지 않는 토큰을 조회할 때") {
            Then("예외가 발생해야 한다") {
                shouldThrow<TokenNotFoundException> {
                    tokenService.getTokenQueueStatus("invalid-token")
                }
            }
        }
    }

    Given("대용량 동시 접속 시나리오에서") {
        When("100명의 사용자가 동시에 토큰을 요청할 때") {
            val userCount = 100
            val executor = Executors.newFixedThreadPool(20)
            val successCount = AtomicInteger(0)
            val failureCount = AtomicInteger(0)
            
            // 사용자들 미리 생성
            val userIds = (6000L..6000L + userCount).toList()
            userIds.forEach { userId ->
                val user = User.create(userId)
                userJpaRepository.save(user)
            }
            
            val futures = userIds.map { userId ->
                CompletableFuture.supplyAsync({
                    try {
                        val tokenDetail = tokenService.issueWaitingToken(userId)
                        val queueDetail = tokenService.getTokenQueueStatus(tokenDetail.token)
                        successCount.incrementAndGet()
                        Pair(tokenDetail, queueDetail)
                    } catch (e: Exception) {
                        failureCount.incrementAndGet()
                        null
                    }
                }, executor)
            }
            
            CompletableFuture.allOf(*futures.toTypedArray()).get(60, TimeUnit.SECONDS)

            Then("시스템이 안정적으로 처리해야 한다") {
                successCount.get() shouldBe userCount
                failureCount.get() shouldBe 0
                
                println("대용량 동시 접속 테스트 결과:")
                println("- 성공한 토큰 발급: ${successCount.get()}")
                println("- 실패한 토큰 발급: ${failureCount.get()}")
            }
        }
    }

    Given("토큰 정리 및 관리 시나리오에서") {
        When("만료된 토큰들을 일괄 정리할 때") {
            val tokenCount = 50
            val expiredTokens = mutableListOf<String>()
            
            // 토큰들 생성 후 일부 만료
            repeat(tokenCount) { index ->
                val userId = (7000L + index)
                val user = User.create(userId)
                userJpaRepository.save(user)
                
                val tokenDetail = tokenService.issueWaitingToken(userId)
                
                // 50%의 토큰을 만료시킴
                if (index % 2 == 0) {
                    tokenLifecycleManager.expireToken(tokenDetail.token)
                    expiredTokens.add(tokenDetail.token)
                }
            }
            
            // 만료된 토큰 정리
            val cleanedCount = tokenLifecycleManager.cleanupExpiredTokens()

            Then("만료된 토큰들이 정리되어야 한다") {
                cleanedCount shouldBe expiredTokens.size
                
                // 만료된 토큰들의 상태 확인
                expiredTokens.forEach { tokenStr ->
                    val status = redisTokenStore.getTokenStatus(tokenStr)
                    status shouldBe TokenStatus.EXPIRED
                }
            }
        }
    }

    afterSpec {
        println("Auth 도메인 통합 테스트 완료")
        println("- 토큰 발급/관리 플로우 검증 완료")
        println("- 대기열 관리 및 동시성 처리 검증 완료")
        println("- 토큰 생명주기 관리 검증 완료")
        println("- 대용량 동시 접속 처리 검증 완료")
        println("- Redis 기반 토큰 스토어 통합 검증 완료")
    }
})
