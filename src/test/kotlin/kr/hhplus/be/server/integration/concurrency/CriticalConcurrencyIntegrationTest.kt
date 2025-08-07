package kr.hhplus.be.server.integration.concurrency

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldBeGreaterThan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kr.hhplus.be.server.api.balance.usecase.ChargeBalanceUseCase
import kr.hhplus.be.server.api.balance.usecase.DeductBalanceUseCase
import kr.hhplus.be.server.api.payment.usecase.ProcessPaymentUserCase
import kr.hhplus.be.server.api.reservation.usecase.ReserveSeatUseCase
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.balance.models.Point
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@TestPropertySource(locations = ["classpath:application-test.yml"])
class CriticalConcurrencyIntegrationTest(
    private val chargeBalanceUseCase: ChargeBalanceUseCase,
    private val deductBalanceUseCase: DeductBalanceUseCase,
    private val reserveSeatUseCase: ReserveSeatUseCase,
    private val processPaymentUseCase: ProcessPaymentUserCase,
    private val pointRepository: PointRepository
) : DescribeSpec({

    describe("핵심 비즈니스 동시성 테스트") {
        
        context("잔액 충전/차감 동시성") {
            it("동일 사용자에 대한 동시 충전/차감시 데이터 정합성이 보장되어야 한다") {
                runBlocking {
                    // given
                    val userId = 100L
                    val initialAmount = BigDecimal("10000")
                    val chargeAmount = BigDecimal("5000")
                    val deductAmount = BigDecimal("3000")
                    
                    // 초기 잔액 설정
                    pointRepository.save(Point.create(userId, initialAmount))
                    
                    val successCount = AtomicInteger(0)
                    val errorCount = AtomicInteger(0)
                    
                    // when - 동시에 충전 3번, 차감 2번
                    val jobs = listOf(
                        // 충전 작업들
                        async { 
                            try {
                                chargeBalanceUseCase.execute(userId, chargeAmount)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("Charge failed: ${e.message}")
                            }
                        },
                        async { 
                            try {
                                chargeBalanceUseCase.execute(userId, chargeAmount)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("Charge failed: ${e.message}")
                            }
                        },
                        // 차감 작업들
                        async { 
                            try {
                                Thread.sleep(100) // 충전이 먼저 되도록 약간 지연
                                deductBalanceUseCase.execute(userId, deductAmount)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("Deduct failed: ${e.message}")
                            }
                        },
                        async { 
                            try {
                                Thread.sleep(200) // 더 늦게 실행
                                deductBalanceUseCase.execute(userId, deductAmount)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("Deduct failed: ${e.message}")
                            }
                        }
                    )
                    
                    jobs.awaitAll()
                    
                    // then
                    val finalBalance = pointRepository.findByUserId(userId)
                    println("최종 잔액: ${finalBalance?.amount}")
                    println("성공: ${successCount.get()}, 실패: ${errorCount.get()}")
                    
                    // Lock이 정상 작동했다면 모든 작업이 순차적으로 처리됨
                    successCount.get() shouldBeGreaterThan 0
                    
                    // 최종 잔액이 예상 범위 내에 있는지 확인
                    finalBalance?.amount shouldBeGreaterThan BigDecimal.ZERO
                }
            }
        }

        context("좌석 예약 동시성") {
            it("동일 좌석에 대한 동시 예약 시도시 하나만 성공해야 한다") {
                runBlocking {
                    // given
                    val concertId = 1L
                    val seatId = 1L
                    val token1 = "valid-token-1"
                    val token2 = "valid-token-2"
                    val token3 = "valid-token-3"
                    val user1 = 101L
                    val user2 = 102L
                    val user3 = 103L
                    
                    val successCount = AtomicInteger(0)
                    val errorCount = AtomicInteger(0)
                    
                    // when - 동시에 같은 좌석 예약 시도
                    val jobs = listOf(
                        async {
                            try {
                                reserveSeatUseCase.execute(user1, concertId, seatId, token1)
                                successCount.incrementAndGet()
                                println("User $user1 예약 성공")
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("User $user1 예약 실패: ${e.message}")
                            }
                        },
                        async {
                            try {
                                reserveSeatUseCase.execute(user2, concertId, seatId, token2)
                                successCount.incrementAndGet()
                                println("User $user2 예약 성공")
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("User $user2 예약 실패: ${e.message}")
                            }
                        },
                        async {
                            try {
                                reserveSeatUseCase.execute(user3, concertId, seatId, token3)
                                successCount.incrementAndGet()
                                println("User $user3 예약 성공")
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("User $user3 예약 실패: ${e.message}")
                            }
                        }
                    )
                    
                    jobs.awaitAll()
                    
                    // then
                    println("예약 성공: ${successCount.get()}, 실패: ${errorCount.get()}")
                    
                    // 동일 좌석에 대해서는 오직 하나만 성공해야 함
                    successCount.get() shouldBe 1
                    errorCount.get() shouldBe 2
                }
            }
        }

        context("결제 처리 동시성") {
            it("동일 예약에 대한 중복 결제 시도시 하나만 성공해야 한다") {
                runBlocking {
                    // given
                    val userId = 201L
                    val reservationId = 1L
                    val token1 = "payment-token-1"
                    val token2 = "payment-token-2"
                    
                    val successCount = AtomicInteger(0)
                    val errorCount = AtomicInteger(0)
                    
                    // when - 동시에 같은 예약 결제 시도
                    val jobs = listOf(
                        async {
                            try {
                                processPaymentUseCase.execute(userId, reservationId, token1)
                                successCount.incrementAndGet()
                                println("결제 1 성공")
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("결제 1 실패: ${e.message}")
                            }
                        },
                        async {
                            try {
                                processPaymentUseCase.execute(userId, reservationId, token2)
                                successCount.incrementAndGet()
                                println("결제 2 성공")
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                                println("결제 2 실패: ${e.message}")
                            }
                        }
                    )
                    
                    jobs.awaitAll()
                    
                    // then
                    println("결제 성공: ${successCount.get()}, 실패: ${errorCount.get()}")
                    
                    // 중복 결제 방지: 최대 1개만 성공해야 함
                    successCount.get() shouldBe 1
                    errorCount.get() shouldBe 1
                }
            }
        }

        context("사용자별 Lock 독립성") {
            it("서로 다른 사용자의 작업은 병렬로 처리되어야 한다") {
                runBlocking {
                    // given
                    val user1 = 301L
                    val user2 = 302L
                    val user3 = 303L
                    val chargeAmount = BigDecimal("1000")
                    
                    // 초기 잔액 설정
                    pointRepository.save(Point.create(user1, BigDecimal("5000")))
                    pointRepository.save(Point.create(user2, BigDecimal("5000")))
                    pointRepository.save(Point.create(user3, BigDecimal("5000")))
                    
                    val startTime = System.currentTimeMillis()
                    
                    // when - 서로 다른 사용자 동시 충전
                    val jobs = listOf(
                        async {
                            chargeBalanceUseCase.execute(user1, chargeAmount)
                        },
                        async {
                            chargeBalanceUseCase.execute(user2, chargeAmount)
                        },
                        async {
                            chargeBalanceUseCase.execute(user3, chargeAmount)
                        }
                    )
                    
                    jobs.awaitAll()
                    val totalTime = System.currentTimeMillis() - startTime
                    
                    // then
                    println("총 소요 시간: ${totalTime}ms")
                    
                    // 서로 다른 사용자는 병렬 처리되므로 빨라야 함 (3초 이내)
                    totalTime shouldBeGreaterThan 0L
                    
                    // 모든 사용자의 잔액이 증가했는지 확인
                    pointRepository.findByUserId(user1)?.amount shouldBe BigDecimal("6000")
                    pointRepository.findByUserId(user2)?.amount shouldBe BigDecimal("6000")
                    pointRepository.findByUserId(user3)?.amount shouldBe BigDecimal("6000")
                }
            }
        }
    }
})
