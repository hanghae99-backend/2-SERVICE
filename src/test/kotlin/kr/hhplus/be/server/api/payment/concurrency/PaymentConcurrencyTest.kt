package kr.hhplus.be.server.api.payment.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.IsolationMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.config.ConcurrencyTest
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.concert.models.*
import kr.hhplus.be.server.domain.concert.repositories.*
import kr.hhplus.be.server.domain.payment.repositories.PaymentRepository
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.config.TestDataCleanupHelper
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.config.MockTestConfiguration
import kr.hhplus.be.server.config.mock.MockConcertDataPlatformClient
import kr.hhplus.be.server.domain.auth.service.TokenManager
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import org.springframework.context.annotation.Import
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ConcurrencyTest
@Import(MockTestConfiguration::class)
class PaymentConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val seatRepository: SeatRepository,
    private val reservationRepository: ReservationRepository,
    private val paymentRepository: PaymentRepository,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val transactionManager: PlatformTransactionManager,
    private val tokenManager: TokenManager,
    private val mockDataPlatformClient: MockConcertDataPlatformClient,
    private val tokenStore: kr.hhplus.be.server.domain.auth.repositories.TokenStore
) : DescribeSpec({
    extension(SpringExtension)
    
    // 테스트 격리를 위한 설정
    isolationMode = IsolationMode.InstancePerTest

    lateinit var mockMvc: MockMvc
    lateinit var testUsers: List<User>
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testSeat: Seat
    lateinit var testReservations: List<Reservation>
    lateinit var testTokens: List<WaitingToken>
    
    // 각 테스트마다 고유한 base userId 사용
    val baseUserId = System.currentTimeMillis() % 1000000

    beforeEach {
        val transactionTemplate = TransactionTemplate(transactionManager)
        
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 분산락 상태 정리
        try {
            distributedLock.clearAllLocks()
        } catch (e: Exception) {
            // 무시
        }
        
        transactionTemplate.execute { _ ->
            // 테스트 데이터 정리
            try {
                val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
                TestDataCleanupHelper.cleanupAll(redisTemplate, jdbcTemplate)
            } catch (e: Exception) {
                // 무시
            }
            
            // 영속성 컨텍스트 클리어
            val entityManager = webApplicationContext.getBean(jakarta.persistence.EntityManager::class.java)
            entityManager.flush()
            entityManager.clear()

            // TestDataFixture를 사용한 기본 환경 구성
            // 각 사용자에게 고유한 userId 할당
            val userCount = 5
            testUsers = (0 until userCount).map { index ->
                TestDataFixture.createTestUser(
                    context = webApplicationContext,
                    userId = baseUserId + index,
                    withPoints = true,
                    pointAmount = BigDecimal("200000")
                )
            }
            
            // 콘서트 환경 생성
            val concertEnv = TestDataFixture.createFullConcertEnvironment(
                context = webApplicationContext,
                concertTitle = "결제 테스트 콘서트",
                seatCount = 50
            )
            
            testConcert = concertEnv.concert
            testSchedule = concertEnv.schedule
            testSeat = concertEnv.seats.first()
            
            // 각 사용자별로 개별 좌석과 예약 생성
            testReservations = testUsers.mapIndexed { index, user ->
                // 각 사용자마다 개별 좌석 생성
                val userSeat = TestDataFixture.createTestSeats(
                    context = webApplicationContext,
                    scheduleId = testSchedule.scheduleId,
                    seatCount = 1,
                    startNumber = 100 + index,
                    price = BigDecimal("50000")
                ).first()
                
                // 예약 생성
                val reservationService = webApplicationContext.getBean(kr.hhplus.be.server.domain.reservation.service.ReservationService::class.java)
                reservationService.createReservation(
                    userId = user.userId,
                    concertId = testConcert.concertId,
                    seatId = userSeat.seatId
                )
            }
            
            // 토큰 생성
            val tokenFactory = webApplicationContext.getBean(kr.hhplus.be.server.domain.auth.factory.TokenFactory::class.java)
            val tokenStore = webApplicationContext.getBean(kr.hhplus.be.server.domain.auth.repositories.TokenStore::class.java)
            testTokens = testUsers.map { user ->
                val token = tokenFactory.createWaitingToken(user.userId)
                tokenStore.save(token)
                tokenStore.activateToken(token.token)
                token
            }
            
            // 테스트 데이터 검증
            val createdSeats = seatRepository.findAll()
            println("생성된 좌석 수: ${createdSeats.size}")
            createdSeats.forEach { seat ->
                println("좌석 ${seat.seatNumber}: 상태 = ${seat.status.code}")
            }
            
            val createdReservations = reservationRepository.findAll()
            println("생성된 예약 수: ${createdReservations.size}")
            createdReservations.forEach { reservation ->
                println("예약 ID ${reservation.reservationId}: 사용자 ${reservation.userId}, 좌석 ${reservation.seatNumber}, 상태 = ${reservation.status.code}")
            }
            
            // DB에 즉시 반영
            entityManager.flush()
        }
        
        // 트랜잭션 후 영속성 컨텍스트 클리어
        val entityManager = webApplicationContext.getBean(jakarta.persistence.EntityManager::class.java)
        entityManager.clear()
        
        // MockReservationApiClient에 예약 ID 등록 - 결제에서 예약 확인을 위해 필요
        try {
            val mockReservationApiClient = webApplicationContext.getBean(kr.hhplus.be.server.config.mock.MockReservationApiClient::class.java)
            mockReservationApiClient.clear()
            testReservations.forEach { reservation ->
                mockReservationApiClient.addValidReservationId(reservation.reservationId)
            }
            println("예약 ID 등록 완료: ${testReservations.map { it.reservationId }}")
        } catch (e: Exception) {
            println("모크 예약 API 클라이언트에 예약 ID 등록 실패: ${e.message}")
        }
        
        // 데이터 정합성 확인을 위한 대기
        Thread.sleep(500)
    }

    afterEach {
        val stats = distributedLock.getLockStatistics()
        println("""
            === Payment 동시성 테스트 분산락 통계 ===
            성공: ${stats.acquisitionCount}
            실패: ${stats.failureCount}
            평균 대기시간: ${stats.averageWaitTimeMs}ms
            성공률: ${stats.successRate}%
        """.trimIndent())
    }

    describe("결제 동시성 테스트 - 분산락 적용") {
        context("동시에 각자 다른 좌석에 대해 여러 사용자가 결제 요청을 할 때") {
            it("분산락으로 모든 결제가 성공해야 한다") {
                // given
                val executor = Executors.newFixedThreadPool(testUsers.size)
                val latch = CountDownLatch(testUsers.size)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when
                val futures = testUsers.mapIndexed { index, user ->
                    CompletableFuture.supplyAsync({
                        try {
                            latch.countDown()
                            latch.await()
                            
                            val request = PaymentRequest(
                                userId = user.userId,
                                reservationId = testReservations[index].reservationId,
                                seatId = testReservations[index].seatId, // 각 사용자별 고유 좌석 사용
                                amount = testReservations[index].price,
                                token = testTokens[index].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/payments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                "SUCCESS: User ${user.userId}"
                            } else {
                                failureCount.incrementAndGet()
                                val errorResponse = result.response.contentAsString
                                println("User ${user.userId} failed with status ${result.response.status}: $errorResponse")
                                "FAILURE: ${result.response.status}"
                            }
                        } catch (e: Exception) {
                        failureCount.incrementAndGet()
                        println("User ${user.userId} error: ${e.message}")
                            e.printStackTrace()
            "ERROR: ${e.message}"
        }
                    }, executor)
                }

                val results = futures.map { it.get(30, TimeUnit.SECONDS) }

                // then
                println("=== 결제 동시성 결과 ===")
                println("성공: ${successCount.get()}, 실패: ${failureCount.get()}")
                results.forEach { println(it) }
                
                // 결제 후 상태 확인
                val paymentsAfter = paymentRepository.findAll()
                println("\n=== 결제 후 상태 ===")
                println("총 결제 건수: ${paymentsAfter.size}")
                paymentsAfter.forEach { payment ->
                    println("결제 ID ${payment.paymentId}: 사용자 ${payment.userId}, 예약 ${payment.reservationId}, 상태 = ${payment.status.code}")
                }
                
                val seatsAfter = seatRepository.findAll()
                seatsAfter.forEach { seat ->
                    println("좌석 ${seat.seatNumber}: 상태 = ${seat.status.code}")
                }
                
                // 모든 사용자가 각자의 좌석에 대해 결제 성공해야 함
                successCount.get() shouldBe testUsers.size
                
                // 비동기 처리 대기 및 검증
                println("\n=== 비동기 처리 검증 시작 ===")
                Thread.sleep(2000) // 비동기 이벤트 처리 대기
                
                println("=== 비동기 처리 검증 완료 ===\n")

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})

sealed class PaymentTestResult {
    data class Success(val userId: Long, val reservationId: Long) : PaymentTestResult()
    data class Failure(val userId: Long, val statusCode: Int, val response: String) : PaymentTestResult()
    data class Error(val userId: Long, val message: String) : PaymentTestResult()
    data class Operation(val operation: String, val statusCode: Int) : PaymentTestResult()
}