package kr.hhplus.be.server.api.concert.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.concert.dto.request.SearchConcertRequest
import kr.hhplus.be.server.config.ConcurrencyTest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertScheduleJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatStatusTypeJpaRepository
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.config.TestDataCleanupService
import kr.hhplus.be.server.domain.user.model.User
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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

@ConcurrencyTest
class ConcertConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val seatStatusTypeJpaRepository: SeatStatusTypeJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val objectMapper: ObjectMapper,
    private val testDataCleanupService: TestDataCleanupService // 추가
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testUsers: List<User>
    lateinit var testTokens: List<WaitingToken>

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // TestDataCleanupService를 사용하여 데이터 정리
        testDataCleanupService.cleanupAllTestData()

        // 테스트 데이터 생성
        testConcert = concertJpaRepository.save(
            Concert.create("동시성 테스트 콘서트", "테스트 아티스트")
        )
        
        testSchedule = concertScheduleJpaRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(10),
                venue = "테스트 홀",
                totalSeats = 50
            )
        )

        // 테스트 사용자들 생성
        val baseUserId = System.currentTimeMillis() + 1000
        testUsers = (0..9).map { index ->
            val userId = baseUserId + index
            userJpaRepository.save(User.create(userId))
        }

        // 테스트 토큰들 생성 (활성화된 토큰들)
        testTokens = testUsers.map { user ->
            val token = tokenFactory.createWaitingToken(user.userId)
            tokenStore.save(token)
            // 토큰을 활성화 상태로 변경
            tokenStore.activateToken(token.token)
            token
        }
        
        // 데이터가 다른 트랜잭션에서 보이도록 잠시 대기
        Thread.sleep(100)
    }

    describe("콘서트 좌석 예약 동시성 테스트") {
        context("여러 사용자가 동시에 같은 좌석을 예약하려고 할 때") {
            it("하나의 예약만 성공해야 한다") {
                // given
                val seatPrice = BigDecimal("80000")
                
                // 좌석 상태 타입 생성
                val availableStatus = seatStatusTypeJpaRepository.save(
                    SeatStatusType(
                        code = "AVAILABLE",
                        name = "예약 가능",
                        description = "예약 가능한 좌석"
                    )
                )
                
                val testSeat = seatJpaRepository.save(
                    Seat.create(
                        scheduleId = testSchedule.scheduleId,
                        seatNumber = "A1",
                        price = seatPrice,
                        availableStatus = availableStatus
                    )
                )

                val executor = Executors.newFixedThreadPool(testUsers.size)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when - 모든 사용자가 동시에 같은 좌석 예약 시도
                val futures = testUsers.mapIndexed { index, user ->
                    CompletableFuture.supplyAsync({
                        try {
                            val request = ReservationCreateRequest(
                                userId = user.userId,
                                concertId = testConcert.concertId,
                                seatId = testSeat.seatId,
                                token = testTokens[index].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                "SUCCESS"
                            } else {
                                failureCount.incrementAndGet()
                                "FAILURE:${result.response.status}"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR:${e.message}"
                        }
                    }, executor)
                }

                // 모든 요청 완료 대기
                CompletableFuture.allOf(*futures.toTypedArray()).join()

        // then
        println("Concert test - Success: ${successCount.get()}, Failure: ${failureCount.get()}")
        println("Future results: ${futures.map { it.get() }}")
        
        // 데이터베이스에서 실제로 생성된 예약 수 확인
        Thread.sleep(1000) // DB 반영 대기
        val actualReservationsInDb = seatJpaRepository.findAll().filter { it.status.code == "RESERVED" }
        println("실제 DB에서 예약된 좌석 수: ${actualReservationsInDb.size}")
        
        // 하나의 좌석에 대해 하나의 예약만 성공해야 함
        val actualSuccessCount = successCount.get()
        println("Expected: 1, Actual: $actualSuccessCount")
        
        // 동시성 제어가 완벽하지 않을 수 있으므로 최소 1개는 성공, 최대 1개만 성공하도록 수정
        actualSuccessCount shouldBe 1
        failureCount.get() shouldBe (testUsers.size - actualSuccessCount)

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("여러 사용자가 동시에 콘서트를 조회할 때") {
            it("모든 조회 요청이 성공적으로 처리되어야 한다") {
                // given
                val executor = Executors.newFixedThreadPool(testUsers.size)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when - 동시에 콘서트 목록 조회
                val futures = testUsers.mapIndexed { index, user ->
                    CompletableFuture.supplyAsync({
                        try {
                            val result = mockMvc.perform(
                                get("/api/v1/concerts")
                                    .header("Authorization", "Bearer ${testTokens[index].token}")
                                    .contentType(MediaType.APPLICATION_JSON)
                            ).andReturn()

                            if (result.response.status == 200) {
                                successCount.incrementAndGet()
                                "SUCCESS"
                            } else {
                                failureCount.incrementAndGet()
                                "FAILURE:${result.response.status}"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR:${e.message}"
                        }
                    }, executor)
                }

                // 모든 요청 완료 대기
                CompletableFuture.allOf(*futures.toTypedArray()).join()

                // then - 모든 조회가 성공해야 함
                successCount.get() shouldBe testUsers.size
                failureCount.get() shouldBe 0

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("여러 사용자가 동시에 콘서트 좌석 목록을 조회할 때") {
            it("일관된 좌석 정보를 반환해야 한다") {
                // given - 테스트용 좌석들 생성
                val availableStatus = seatStatusTypeJpaRepository.save(
                    SeatStatusType(
                        code = "AVAILABLE",
                        name = "예약 가능",
                        description = "예약 가능한 좌석"
                    )
                )
                
                val seats = (1..5).map { seatNum ->
                    seatJpaRepository.save(
                        Seat.create(
                            scheduleId = testSchedule.scheduleId,
                            seatNumber = "B$seatNum",
                            price = BigDecimal("50000"),
                            availableStatus = availableStatus
                        )
                    )
                }

                val executor = Executors.newFixedThreadPool(testUsers.size)
                val results = mutableListOf<CompletableFuture<String>>()

                // when - 동시에 좌석 목록 조회
                testUsers.forEachIndexed { index, user ->
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val result = mockMvc.perform(
                                get("/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats", 
                                    testConcert.concertId, testSchedule.scheduleId)
                                    .header("Authorization", "Bearer ${testTokens[index].token}")
                                    .contentType(MediaType.APPLICATION_JSON)
                            ).andReturn()

                            if (result.response.status == 200) {
                                val responseContent = result.response.contentAsString
                                val responseJson = objectMapper.readTree(responseContent)
                                val seatCount = responseJson.get("data").size()
                                "SUCCESS:$seatCount"
                            } else {
                                "FAILURE:${result.response.status}"
                            }
                        } catch (e: Exception) {
                            "ERROR:${e.message}"
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(10, TimeUnit.SECONDS) }

                // then - 모든 응답이 동일한 좌석 수를 반환해야 함
                val successResults = finalResults.filter { it.startsWith("SUCCESS:") }
                successResults.size shouldBe testUsers.size

                // 모든 응답이 동일한 좌석 수를 반환하는지 확인
                val seatCounts = successResults.map { it.split(":")[1].toInt() }.distinct()
                seatCounts.size shouldBe 1  // 모든 응답이 동일한 좌석 수
                seatCounts.first() shouldBe seats.size

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})
