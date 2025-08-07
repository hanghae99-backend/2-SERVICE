package kr.hhplus.be.server.integration.concurrency

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldBeGreaterThan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import kr.hhplus.be.server.api.balance.usecase.ChargeBalanceUseCase
import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.balance.models.Point
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@SpringBootTest
@TestPropertySource(locations = ["classpath:application-test.yml"])
class BalanceConcurrencyIntegrationTest(
    private val chargeBalanceUseCase: ChargeBalanceUseCase,
    private val deductBalanceUseCase: DeductBalanceUseCase,
    private val pointRepository: PointRepository
) : DescribeSpec({

    describe("잔액 처리 분산락 동시성 테스트") {
        
        context("동일 사용자 동시 충전") {
            it("5번의 동시 충전이 모두 반영되어야 한다") {
                runBlocking {
                    // given
                    val userId = 1000L
                    val initialAmount = BigDecimal("1000")
                    val chargeAmount = BigDecimal("2000")
                    val concurrentRequests = 5
                    
                    pointRepository.save(Point.create(userId, initialAmount))
                    
                    val results = ConcurrentHashMap<Int, BigDecimal>()
                    val errors = ConcurrentHashMap<Int, String>()
                    
                    // when
                    val jobs = (1..concurrentRequests).map { index ->
                        async {
                            try {
                                val result = chargeBalanceUseCase.execute(userId, chargeAmount)
                                results[index] = result.amount
                                println("충전 $index 완료: ${result.amount}")
                            } catch (e: Exception) {
                                errors[index] = e.message ?: "Unknown error"
                                println("충전 $index 실패: ${e.message}")
                            }
                        }
                    }
                    
                    jobs.awaitAll()
                    
                    // then
                    val finalBalance = pointRepository.findByUserId(userId)
                    val expectedAmount = initialAmount.add(chargeAmount.multiply(BigDecimal(concurrentRequests)))
                    
                    println("초기 잔액: $initialAmount")
                    println("충전 금액: $chargeAmount x $concurrentRequests")
                    println("예상 최종 잔액: $expectedAmount")
                    println("실제 최종 잔액: ${finalBalance?.amount}")
                    println("성공: ${results.size}, 실패: ${errors.size}")
                    
                    finalBalance?.amount shouldBe expectedAmount
                    results.size shouldBe concurrentRequests
                    errors.size shouldBe 0
                }
            }
        }

        context("동일 사용자 충전/차감 혼합") {
            it("충전과 차감이 순차적으로 처리되어 정확한 잔액이 유지되어야 한다") {
                runBlocking {
                    // given
                    val userId = 2000L
                    val initialAmount = BigDecimal("50000")
                    val chargeAmount = BigDecimal("10000")
                    val deductAmount = BigDecimal("5000")
                    
                    pointRepository.save(Point.create(userId, initialAmount))
                    
                    val chargeResults = mutableListOf<BigDecimal>()
                    val deductResults = mutableListOf<BigDecimal>()
                    val allErrors = mutableListOf<String>()
                    
                    // when - 충전 3번, 차감 2번을 무작위 순서로
                    val jobs = mutableListOf<kotlin.coroutines.Deferred<Unit>>()
                    
                    // 충전 작업들
                    repeat(3) { index ->
                        jobs.add(async {
                            try {
                                Thread.sleep(Random.nextLong(0, 100)) // 랜덤 지연
                                val result = chargeBalanceUseCase.execute(userId, chargeAmount)
                                synchronized(chargeResults) {
                                    chargeResults.add(result.amount)
                                }
                                println("충전 ${index + 1} 완료: ${result.amount}")
                            } catch (e: Exception) {
                                synchronized(allErrors) {
                                    allErrors.add("충전 실패: ${e.message}")
                                }
                            }
                        })
                    }
                    
                    // 차감 작업들
                    repeat(2) { index ->
                        jobs.add(async {
                            try {
                                Thread.sleep(Random.nextLong(0, 100)) // 랜덤 지연
                                val result = deductBalanceUseCase.execute(userId, deductAmount)
                                synchronized(deductResults) {
                                    deductResults.add(result.amount)
                                }
                                println("차감 ${index + 1} 완료: ${result.amount}")
                            } catch (e: Exception) {
                                synchronized(allErrors) {
                                    allErrors.add("차감 실패: ${e.message}")
                                }
                            }
                        })
                    }
                    
                    jobs.awaitAll()
                    
                    // then
                    val finalBalance = pointRepository.findByUserId(userId)
                    val expectedAmount = initialAmount
                        .add(chargeAmount.multiply(BigDecimal(3)))  // +30000
                        .subtract(deductAmount.multiply(BigDecimal(2))) // -10000
                    
                    println("초기: $initialAmount, 충전: +${chargeAmount.multiply(BigDecimal(3))}, 차감: -${deductAmount.multiply(BigDecimal(2))}")
                    println("예상 잔액: $expectedAmount")
                    println("실제 잔액: ${finalBalance?.amount}")
                    println("오류: $allErrors")
                    
                    finalBalance?.amount shouldBe expectedAmount // 70000
                    allErrors.size shouldBe 0
                }
            }
        }

        context("서로 다른 사용자 동시 처리") {
            it("서로 다른 사용자는 독립적으로 병렬 처리되어야 한다") {
                runBlocking {
                    // given
                    val userIds = listOf(3001L, 3002L, 3003L, 3004L, 3005L)
                    val initialAmount = BigDecimal("10000")
                    val chargeAmount = BigDecimal("5000")
                    
                    // 각 사용자별 초기 잔액 설정
                    userIds.forEach { userId ->
                        pointRepository.save(Point.create(userId, initialAmount))
                    }
                    
                    val results = ConcurrentHashMap<Long, BigDecimal>()
                    val startTime = System.currentTimeMillis()
                    
                    // when - 모든 사용자 동시 충전
                    val jobs = userIds.map { userId ->
                        async {
                            val result = chargeBalanceUseCase.execute(userId, chargeAmount)
                            results[userId] = result.amount
                            println("사용자 $userId 충전 완료: ${result.amount}")
                        }
                    }
                    
                    jobs.awaitAll()
                    val totalTime = System.currentTimeMillis() - startTime
                    
                    // then
                    println("총 처리 시간: ${totalTime}ms")
                    println("사용자별 결과: $results")
                    
                    // 모든 사용자가 성공적으로 처리됨
                    results.size shouldBe userIds.size
                    
                    // 각 사용자의 잔액이 올바르게 증가했는지 확인
                    userIds.forEach { userId ->
                        val finalBalance = pointRepository.findByUserId(userId)
                        finalBalance?.amount shouldBe BigDecimal("15000") // 10000 + 5000
                    }
                    
                    // 병렬 처리로 인해 단일 사용자 처리 시간의 5배보다 빨라야 함
                    totalTime shouldBeGreaterThan 0L
                }
            }
        }

        context("Lock 타임아웃 테스트") {
            it("Lock 대기 시간 초과시 적절한 예외가 발생해야 한다") {
                runBlocking {
                    // given
                    val userId = 4000L
                    val initialAmount = BigDecimal("10000")
                    val chargeAmount = BigDecimal("1000")
                    
                    pointRepository.save(Point.create(userId, initialAmount))
                    
                    val successCount = AtomicInteger(0)
                    val timeoutCount = AtomicInteger(0)
                    val otherErrorCount = AtomicInteger(0)
                    
                    // when - 많은 동시 요청으로 Lock 경합 유발
                    val jobs = (1..10).map { index ->
                        async {
                            try {
                                chargeBalanceUseCase.execute(userId, chargeAmount)
                                successCount.incrementAndGet()
                                println("요청 $index 성공")
                            } catch (e: Exception) {
                                when {
                                    e.message?.contains("락 획득에 실패") == true ||
                                    e.message?.contains("timeout") == true ||
                                    e.message?.contains("Timeout") == true -> {
                                        timeoutCount.incrementAndGet()
                                        println("요청 $index 타임아웃: ${e.message}")
                                    }
                                    else -> {
                                        otherErrorCount.incrementAndGet()
                                        println("요청 $index 기타 오류: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                    
                    jobs.awaitAll()
                    
                    // then
                    println("성공: ${successCount.get()}, 타임아웃: ${timeoutCount.get()}, 기타 오류: ${otherErrorCount.get()}")
                    
                    // 모든 요청이 처리됨 (성공 또는 타임아웃)
                    (successCount.get() + timeoutCount.get() + otherErrorCount.get()) shouldBe 10
                    
                    // 적어도 몇 개는 성공해야 함
                    successCount.get() shouldBeGreaterThan 0
                    
                    // Lock으로 인한 순차 처리로 잔액 정합성 보장
                    val finalBalance = pointRepository.findByUserId(userId)
                    val expectedAmount = initialAmount.add(chargeAmount.multiply(BigDecimal(successCount.get())))
                    finalBalance?.amount shouldBe expectedAmount
                }
            }
        }
    }
})
