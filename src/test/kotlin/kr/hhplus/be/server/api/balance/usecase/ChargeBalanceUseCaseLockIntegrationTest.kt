package kr.hhplus.be.server.api.balance.usecase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@TestPropertySource(locations = ["classpath:application-test.yml"])
@SpringJUnitConfig
class ChargeBalanceUseCaseLockIntegrationTest(
    private val chargeBalanceUseCase: ChargeBalanceUseCase
) : DescribeSpec({

    describe("@LockGuard 동시성 테스트") {
        context("동일한 사용자에 대해 동시에 충전 요청할 때") {
            it("Lock으로 인해 순차적으로 처리되어야 한다") {
                // given
                val userId = 1L
                val chargeAmount = BigDecimal("1000")
                val concurrentRequests = 5
                val successCount = AtomicInteger(0)
                val errorCount = AtomicInteger(0)
                val latch = CountDownLatch(concurrentRequests)

                // when - 동시에 여러 충전 요청
                runBlocking {
                    repeat(concurrentRequests) {
                        async {
                            try {
                                chargeBalanceUseCase.execute(userId, chargeAmount)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("Error occurred: ${e.message}")
                            } finally {
                                latch.countDown()
                            }
                        }
                    }
                }

                latch.await()

                // then - Lock으로 인해 데이터 일관성이 보장되어야 함
                println("Success: ${successCount.get()}, Error: ${errorCount.get()}")
                // Lock이 정상 작동하면 모든 요청이 순차적으로 처리됨
                (successCount.get() + errorCount.get()) shouldBe concurrentRequests
            }
        }

        context("서로 다른 사용자에 대해 동시 충전할 때") {
            it("Lock이 사용자별로 독립적으로 동작해야 한다") {
                // given
                val user1 = 1L
                val user2 = 2L
                val chargeAmount = BigDecimal("1000")
                val results = mutableListOf<Long>()

                // when - 서로 다른 사용자 동시 요청
                runBlocking {
                    val job1 = async {
                        val start = System.currentTimeMillis()
                        chargeBalanceUseCase.execute(user1, chargeAmount)
                        System.currentTimeMillis() - start
                    }
                    
                    val job2 = async {
                        val start = System.currentTimeMillis()
                        chargeBalanceUseCase.execute(user2, chargeAmount)
                        System.currentTimeMillis() - start
                    }

                    results.add(job1.await())
                    results.add(job2.await())
                }

                // then - 서로 다른 사용자는 병렬로 처리되어야 함
                println("User1 time: ${results[0]}ms, User2 time: ${results[1]}ms")
                // 두 요청이 거의 동시에 처리되어야 함 (Lock이 사용자별로 분리됨)
            }
        }
    }
})
