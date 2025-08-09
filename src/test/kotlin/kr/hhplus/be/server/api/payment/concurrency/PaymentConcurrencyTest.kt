package kr.hhplus.be.server.api.payment.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.concert.models.*
import kr.hhplus.be.server.domain.concert.repositories.*
import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import kr.hhplus.be.server.domain.payment.repository.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repository.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.repository.UserRepository
import kr.hhplus.be.server.config.TestDataCleanupService
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PaymentConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val pointRepository: PointRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatRepository: SeatRepository,
    private val seatStatusTypeRepository: SeatStatusTypePojoRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationStatusTypeRepository: ReservationStatusTypePojoRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentStatusTypeRepository: PaymentStatusTypePojoRepository,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val testDataCleanupService: TestDataCleanupService // 추가
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUsers: List<User>
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testSeat: Seat
    lateinit var testReservations: List<Reservation>
    lateinit var testTokens: List<WaitingToken>

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // TestDataCleanupService를 사용하여 데이터 정리
        testDataCleanupService.cleanupAllTestData()

        // 테스트 데이터 설정
        // 테스트용 사용자들 생성
        val baseUserId = System.currentTimeMillis() + 2000
        testUsers = (0..4).map { index ->
            val userId = baseUserId + index
            val user = userRepository.save(User.create(userId))
            // 각 사용자에게 충분한 포인트 지급
            pointRepository.save(Point.create(user.userId, BigDecimal("1000000")))
            user
        }

        // 포인트 이력 타입 생성
        pointHistoryTypeRepository.save(
            PointHistoryType(
                code = "DEDUCT",
                name = "사용",
                description = "포인트 사용"
            )
        )

        // 콘서트 생성
        testConcert = concertRepository.save(
            Concert.create(
                title = "동시성 테스트 콘서트",
                artist = "테스트 아티스트"
            )
        )

        // 콘서트 스케줄 생성
        testSchedule = concertScheduleRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(30),
                venue = "동시성 테스트 공연장",
                totalSeats = 50
            )
        )

        // 좌석 상태 타입들 생성
        val availableStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = "AVAILABLE",
                name = "예약 가능",
                description = "예약 가능한 좌석"
            )
        )

        val reservedStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = "RESERVED",
                name = "예약됨",
                description = "예약된 좌석"
            )
        )

        // 테스트용 좌석 생성
        testSeat = seatRepository.save(
            Seat.create(
                scheduleId = testSchedule.scheduleId,
                seatNumber = "A1",
                price = BigDecimal("50000"),
                availableStatus = reservedStatus
            )
        )

        // 예약 상태 타입들 생성
        val temporaryStatus = reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "TEMPORARY",
                name = "임시 예약",
                description = "임시 예약 상태"
            )
        )

        reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "CONFIRMED",
                name = "예약 확정",
                description = "결제 완료된 확정 예약"
            )
        )

        // 결제 상태 타입 생성
        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = "COMPLETED",
                name = "결제 완료",
                description = "결제가 성공적으로 완료됨"
            )
        )

        // 테스트용 임시 예약들 생성 (각 사용자마다)
        testReservations = testUsers.map { user ->
            reservationRepository.save(
                Reservation.createTemporary(
                    userId = user.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    seatNumber = testSeat.seatNumber,
                    price = testSeat.price,
                    temporaryStatus = temporaryStatus,
                    tempMinutes = 10
                )
            )
        }

        // 테스트용 토큰들 생성 (활성화된 토큰들)
        testTokens = testUsers.map { user ->
            val token = tokenFactory.createWaitingToken(user.userId)
            tokenStore.activateToken(token.token)
            token
        }
        
        // 데이터가 다른 트랜잭션에서 보이도록 잠시 대기
        Thread.sleep(100)
    }

    describe("결제 동시성 테스트") {
        context("동시에 같은 좌석에 대해 여러 사용자가 결제 요청을 할 때") {
            it("하나의 결제만 성공해야 한다") {
                // given
                val executor = Executors.newFixedThreadPool(testUsers.size)
                val results = mutableListOf<CompletableFuture<PaymentTestResult>>()
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when - 모든 사용자가 동시에 결제 요청
                testUsers.forEachIndexed { index, user ->
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val request = PaymentRequest(
                                userId = user.userId,
                                reservationId = testReservations[index].reservationId,
                                token = testTokens[index].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/payments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                PaymentTestResult.Success(user.userId, testReservations[index].reservationId)
                            } else {
                                failureCount.incrementAndGet()
                                PaymentTestResult.Failure(user.userId, result.response.status, result.response.contentAsString)
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            PaymentTestResult.Error(user.userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(15, TimeUnit.SECONDS) }

                // then - 하나의 결제만 성공해야 함
                println("Payment test - Success: ${successCount.get()}, Failure: ${failureCount.get()}")
                println("Results: ${finalResults}")
                
                val actualSuccessCount = successCount.get()
                actualSuccessCount shouldBe 1
                
                if (actualSuccessCount > 0) {
                    failureCount.get() shouldBe (testUsers.size - actualSuccessCount)
                    
                    // 성공한 결제에 대한 검증
                    val payments = paymentRepository.findAll()
                    println("Total payments created: ${payments.size}")
                    payments.size shouldBe actualSuccessCount
                }

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("동일 사용자가 여러 예약에 대해 동시에 결제할 때") {
            it("포인트 차감이 정확하게 처리되어야 한다") {
                // given - 한 사용자가 여러 좌석 예약
                val user = testUsers[0]
                val initialBalance = BigDecimal("200000")
                
                // 사용자 포인트를 특정 금액으로 설정
                val userPoint = pointRepository.findByUserId(user.userId)!!
                userPoint.amount = initialBalance
                pointRepository.save(userPoint)

                val reservedStatus = seatStatusTypeRepository.findByCode("RESERVED")!!
                val temporaryStatus = reservationStatusTypeRepository.findByCode("TEMPORARY")!!
                
                val multipleSeats = (5..7).map { seatNum ->
                    seatRepository.save(
                        Seat.create(
                            scheduleId = testSchedule.scheduleId,
                            seatNumber = "B${seatNum}",
                            price = BigDecimal("50000"),
                            availableStatus = reservedStatus
                        )
                    )
                }

                val multipleReservations = multipleSeats.map { seat ->
                    reservationRepository.save(
                        Reservation.createTemporary(
                            userId = user.userId,
                            concertId = testConcert.concertId,
                            seatId = seat.seatId,
                            seatNumber = seat.seatNumber,
                            price = seat.price,
                            temporaryStatus = temporaryStatus,
                            tempMinutes = 10
                        )
                    )
                }

                val requestCount = multipleReservations.size
                val executor = Executors.newFixedThreadPool(requestCount)
                val results = mutableListOf<CompletableFuture<PaymentTestResult>>()
                val successCount = AtomicInteger(0)

                // when - 동일 사용자의 동시 결제 요청
                multipleReservations.forEach { reservation ->
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val request = PaymentRequest(
                                userId = user.userId,
                                reservationId = reservation.reservationId,
                                token = testTokens[0].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/payments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                            }
                            PaymentTestResult.Operation("PAYMENT", result.response.status)
                        } catch (e: Exception) {
                            PaymentTestResult.Error(user.userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(20, TimeUnit.SECONDS) }

                // then - 성공한 결제 수만큼 포인트가 차감되어야 함
                val finalBalance = pointRepository.findByUserId(user.userId)!!.amount
                val expectedDeduction = BigDecimal("50000").multiply(BigDecimal(successCount.get()))
                val expectedFinalBalance = initialBalance.subtract(expectedDeduction)

                // BigDecimal 비교 시 compareTo 사용
                println("Initial balance: $initialBalance")
                println("Final balance: $finalBalance")
                println("Expected deduction: $expectedDeduction")
                println("Expected final balance: $expectedFinalBalance")
                println("Success count: ${successCount.get()}")
                
                finalBalance.compareTo(expectedFinalBalance) shouldBe 0

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
    data class Conflict(val userId: Long, val reason: String) : PaymentTestResult()
    data class Operation(val operation: String, val statusCode: Int) : PaymentTestResult()
}
