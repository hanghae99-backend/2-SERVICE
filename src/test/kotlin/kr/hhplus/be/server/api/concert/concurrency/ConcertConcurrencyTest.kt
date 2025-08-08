package kr.hhplus.be.server.api.concert.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.concert.dto.request.SearchConcertRequest
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
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.model.User
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
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
class ConcertConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val seatStatusTypeJpaRepository: SeatStatusTypeJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val objectMapper: ObjectMapper
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

        // 데이터 정리
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()
        userJpaRepository.deleteAll()

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
        testUsers = (1L..10L).map { userId ->
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
    }

    describe("콘서트 좌석 예약 동시성 테스트") {
        context("여러 사용자가 동시에 같은 좌석을 예약하려고 할 때") {
            it("한 명만 성공하고 나머지는 실패해야 한다") {
                // given
                val availableStatus = seatStatusTypeJpaRepository.findByCode("AVAILABLE")!!
                val targetSeat = seatJpaRepository.save(
                    Seat.create(
                        scheduleId = testSchedule.scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        availableStatus = availableStatus
                    )
                )

                val userCount = 5
                val executor = Executors.newFixedThreadPool(userCount)
                val results = mutableListOf<CompletableFuture<ConcertTestResult>>()
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when - 동시에 같은 좌석 예약 시도
                repeat(userCount) { index ->
                    val future = CompletableFuture.supplyAsync<ConcertTestResult>({
                        try {
                            val request = ReservationCreateRequest(
                                userId = testUsers[index].userId,
                                concertId = testConcert.concertId,
                                seatId = targetSeat.seatId,
                                token = testTokens[index].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            when (result.response.status) {
                                201 -> {
                                    successCount.incrementAndGet()
                                    ConcertTestResult.Success(testUsers[index].userId, targetSeat.seatId)
                                }
                                409 -> { // Conflict - 이미 예약된 좌석
                                    failureCount.incrementAndGet()
                                    ConcertTestResult.Conflict(testUsers[index].userId, targetSeat.seatId)
                                }
                                else -> {
                                    failureCount.incrementAndGet()
                                    ConcertTestResult.Failure(testUsers[index].userId, result.response.status, result.response.contentAsString)
                                }
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            ConcertTestResult.Error(testUsers[index].userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(15, TimeUnit.SECONDS) }

                // then - 한 명만 성공해야 함
                successCount.get() shouldBe 1
                failureCount.get() shouldBe (userCount - 1)

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("여러 사용자가 동시에 다른 좌석들을 예약할 때") {
            it("모든 예약이 성공적으로 처리되어야 한다") {
                // given
                val availableStatus = seatStatusTypeJpaRepository.findByCode("AVAILABLE")!!
                val seats = (1..5).map { seatNum ->
                    seatJpaRepository.save(
                        Seat.create(
                            scheduleId = testSchedule.scheduleId,
                            seatNumber = "A${seatNum}",
                            price = BigDecimal("50000"),
                            availableStatus = availableStatus
                        )
                    )
                }

                val executor = Executors.newFixedThreadPool(seats.size)
                val results = mutableListOf<CompletableFuture<ConcertTestResult>>()
                val successCount = AtomicInteger(0)

                // when - 각각 다른 좌석 예약
                seats.forEachIndexed { index, seat ->
                    val future = CompletableFuture.supplyAsync<ConcertTestResult>({
                        try {
                            val request = ReservationCreateRequest(
                                userId = testUsers[index].userId,
                                concertId = testConcert.concertId,
                                seatId = seat.seatId,
                                token = testTokens[index].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                ConcertTestResult.Success(testUsers[index].userId, seat.seatId)
                            } else {
                                ConcertTestResult.Failure(testUsers[index].userId, result.response.status, result.response.contentAsString)
                            }
                        } catch (e: Exception) {
                            ConcertTestResult.Error(testUsers[index].userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(15, TimeUnit.SECONDS) }

                // then - 모든 예약이 성공해야 함
                successCount.get() shouldBe seats.size

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }

    describe("콘서트 조회 동시성 테스트") {
        context("동시에 콘서트 목록을 조회할 때") {
            it("모든 요청이 일관된 결과를 반환해야 한다") {
                // given
                val requestCount = 5
                val executor = Executors.newFixedThreadPool(requestCount)
                val results = mutableListOf<CompletableFuture<String>>()

                // when - 동시에 콘서트 목록 조회
                repeat(requestCount) {
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val result = mockMvc.perform(
                                get("/api/v1/concerts")
                                    .param("startDate", LocalDate.now().toString())
                                    .param("endDate", LocalDate.now().plusDays(30).toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                            ).andReturn()

                            if (result.response.status == 200) {
                                val responseContent = result.response.contentAsString
                                val responseJson = objectMapper.readTree(responseContent)
                                val dataArray = responseJson.get("data")
                                "SUCCESS:${dataArray.size()}"
                            } else {
                                "ERROR:${result.response.status}"
                            }
                        } catch (e: Exception) {
                            "EXCEPTION:${e.message}"
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(10, TimeUnit.SECONDS) }

                // then - 모든 응답이 성공이고 동일한 결과여야 함
                val distinctResults = finalResults.distinct()
                distinctResults.size shouldBe 1
                distinctResults.first().startsWith("SUCCESS:") shouldBe true

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("콘서트 검색과 상세 조회가 동시에 수행될 때") {
            it("데이터 일관성이 보장되어야 한다") {
                // given
                val searchCount = 3
                val detailCount = 3
                val totalThreads = searchCount + detailCount
                val executor = Executors.newFixedThreadPool(totalThreads)
                val results = mutableListOf<CompletableFuture<String>>()

                // when - 검색과 상세 조회를 동시에 수행
                // 검색 요청들
                repeat(searchCount) {
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val request = SearchConcertRequest(
                                keyword = "테스트",
                                startDate = LocalDate.now(),
                                endDate = LocalDate.now().plusDays(30),
                                availableOnly = true
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/concerts/search")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            "SEARCH:${result.response.status}"
                        } catch (e: Exception) {
                            "SEARCH:ERROR:${e.message}"
                        }
                    }, executor)
                    results.add(future)
                }

                // 상세 조회 요청들
                repeat(detailCount) {
                    val future = CompletableFuture.supplyAsync({
                        try {
                            val result = mockMvc.perform(
                                get("/api/v1/concerts/{concertId}", testConcert.concertId)
                                    .contentType(MediaType.APPLICATION_JSON)
                            ).andReturn()

                            "DETAIL:${result.response.status}"
                        } catch (e: Exception) {
                            "DETAIL:ERROR:${e.message}"
                        }
                    }, executor)
                    results.add(future)
                }

                val finalResults = results.map { it.get(15, TimeUnit.SECONDS) }

                // then - 모든 요청이 성공적으로 처리되어야 함
                val searchResults = finalResults.filter { it.startsWith("SEARCH:") }
                val detailResults = finalResults.filter { it.startsWith("DETAIL:") }

                searchResults.count { it == "SEARCH:200" } shouldBe searchCount
                detailResults.count { it == "DETAIL:200" } shouldBe detailCount

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }

    describe("좌석 상태 업데이트 동시성 테스트") {
        context("좌석 예약과 취소가 동시에 발생할 때") {
            it("좌석 상태가 일관되게 관리되어야 한다") {
                // given
                val availableStatus = seatStatusTypeJpaRepository.findByCode("AVAILABLE")!!
                val seats = (1..3).map { seatNum ->
                    seatJpaRepository.save(
                        Seat.create(
                            scheduleId = testSchedule.scheduleId,
                            seatNumber = "B${seatNum}",
                            price = BigDecimal("60000"),
                            availableStatus = availableStatus
                        )
                    )
                }

                val threadCount = 6
                val executor = Executors.newFixedThreadPool(threadCount)
                val results = mutableListOf<CompletableFuture<ConcertTestResult>>()

                // when - 예약과 조회를 동시에 수행
                seats.forEachIndexed { index, seat ->
                    // 예약 요청
                    val reserveFuture = CompletableFuture.supplyAsync<ConcertTestResult>({
                        try {
                            val request = ReservationCreateRequest(
                                userId = testUsers[index].userId,
                                concertId = testConcert.concertId,
                                seatId = seat.seatId,
                                token = testTokens[index].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            ConcertTestResult.Operation("RESERVE", result.response.status)
                        } catch (e: Exception) {
                            ConcertTestResult.Error(testUsers[index].userId, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(reserveFuture)

                    // 좌석 조회 요청
                    val seatCheckFuture = CompletableFuture.supplyAsync<ConcertTestResult>({
                        try {
                            val result = mockMvc.perform(
                                get("/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats",
                                    testConcert.concertId, testSchedule.scheduleId)
                                    .contentType(MediaType.APPLICATION_JSON)
                            ).andReturn()

                            ConcertTestResult.Operation("SEAT_CHECK", result.response.status)
                        } catch (e: Exception) {
                            ConcertTestResult.Error(0L, e.message ?: "Unknown error")
                        }
                    }, executor)
                    results.add(seatCheckFuture)
                }

                val finalResults = results.map { it.get(20, TimeUnit.SECONDS) }

                // then - 모든 작업이 데드락 없이 완료되어야 함
                val reserveResults = finalResults.filterIsInstance<ConcertTestResult.Operation>()
                    .filter { it.operation == "RESERVE" }
                val seatCheckResults = finalResults.filterIsInstance<ConcertTestResult.Operation>()
                    .filter { it.operation == "SEAT_CHECK" }

                reserveResults.size shouldBe seats.size
                seatCheckResults.size shouldBe seats.size

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})

sealed class ConcertTestResult {
    data class Success(val userId: Long, val seatId: Long) : ConcertTestResult()
    data class Failure(val userId: Long, val statusCode: Int, val response: String) : ConcertTestResult()
    data class Error(val userId: Long, val message: String) : ConcertTestResult()
    data class Conflict(val userId: Long, val seatId: Long) : ConcertTestResult()
    data class Operation(val operation: String, val statusCode: Int) : ConcertTestResult()
}
