package kr.hhplus.be.server.api.reservation.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repository.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.repository.UserRepository
import kr.hhplus.be.server.global.extension.orElseThrow
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class ReservationConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val pointRepository: PointRepository,
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatRepository: SeatRepository,
    private val seatStatusTypeRepository: SeatStatusTypePojoRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationStatusTypeRepository: ReservationStatusTypePojoRepository,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUsers: List<User>
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testSeat: Seat
    lateinit var validTokens: List<WaitingToken>
    lateinit var temporaryStatus: ReservationStatusType

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 데이터 정리
        reservationRepository.deleteAll()
        seatRepository.deleteAll()
        concertScheduleRepository.deleteAll()
        concertRepository.deleteAll()
        pointRepository.deleteAll()
        userRepository.deleteAll()

        // 테스트 데이터 설정
        // 여러 테스트용 사용자 생성
        testUsers = (1L..10L).map { userId ->
            val user = userRepository.save(User.Companion.create(userId))
            // 각 사용자에게 충분한 포인트 부여
            pointRepository.save(Point.Companion.create(userId, BigDecimal("500000")))
            user
        }

        // 콘서트 생성
        testConcert = concertRepository.save(
            Concert.Companion.create(
                title = "동시성 테스트 콘서트",
                artist = "테스트 아티스트"
            )
        )

        // 콘서트 스케줄 생성
        testSchedule = concertScheduleRepository.save(
            ConcertSchedule.Companion.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(30),
                venue = "동시성 테스트 공연장",
                totalSeats = 1
            )
        )

        // 좌석 상태 타입 생성
        val availableStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = "AVAILABLE",
                name = "예약 가능",
                description = "예약 가능한 좌석"
            )
        )

        seatStatusTypeRepository.save(
            SeatStatusType(
                code = "RESERVED",
                name = "예약됨",
                description = "예약된 좌석"
            )
        )

        // 단일 좌석 생성 (동시성 테스트용)
        testSeat = seatRepository.save(
            Seat.Companion.create(
                scheduleId = testSchedule.scheduleId,
                seatNumber = "A1",
                price = BigDecimal("50000"),
                availableStatus = availableStatus
            )
        )

        // 예약 상태 타입 생성
        temporaryStatus = reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "TEMPORARY",
                name = "임시 예약",
                description = "임시 예약 상태"
            )
        )

        // 각 사용자에 대한 유효한 토큰 생성
        validTokens = testUsers.map { user ->
            val token = tokenFactory.createWaitingToken(user.userId)
            tokenStore.activateToken(token.token)
            token
        }
    }

    describe("예약 동시성 테스트") {
        context("동일한 좌석에 대해 여러 사용자가 동시에 예약 요청을 할 때") {
            it("하나의 예약만 성공하고 나머지는 실패해야 한다") {
                // given
                val executor = Executors.newFixedThreadPool(10)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when
                val futures = testUsers.mapIndexed { index, user ->
                    CompletableFuture.supplyAsync({
                        try {
                            val request = ReservationCreateRequest(
                                userId = user.userId,
                                concertId = testConcert.concertId,
                                seatId = testSeat.seatId,
                                token = validTokens[index].token
                            )

                            val result = mockMvc.perform(
                                MockMvcRequestBuilders.post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                "SUCCESS"
                            } else {
                                failureCount.incrementAndGet()
                                "FAILURE"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR: ${e.message}"
                        }
                    }, executor)
                }

                // 모든 요청 완료 대기
                CompletableFuture.allOf(*futures.toTypedArray()).join()

                // then
                successCount.get() shouldBe 1  // 정확히 하나의 예약만 성공
                failureCount.get() shouldBe 9  // 나머지 9개는 실패

                // 데이터베이스에서 예약 확인
                val reservations = reservationRepository.findAll()
                reservations.size shouldBe 1

                // 좌석 상태 확인
                val updatedSeat = seatRepository.findById(testSeat.seatId).orElseThrow { RuntimeException("Seat not found") }
                updatedSeat.status.code shouldBe "RESERVED"

                executor.shutdown()
            }
        }

        context("동일한 사용자가 같은 좌석에 대해 동시에 여러 예약 요청을 할 때") {
            it("하나의 예약만 성공해야 한다") {
                // given
                val executor = Executors.newFixedThreadPool(5)
                val user = testUsers.first()
                val token = validTokens.first()
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when
                val futures = (1..5).map {
                    CompletableFuture.supplyAsync({
                        try {
                            val request = ReservationCreateRequest(
                                userId = user.userId,
                                concertId = testConcert.concertId,
                                seatId = testSeat.seatId,
                                token = token.token
                            )

                            val result = mockMvc.perform(
                                MockMvcRequestBuilders.post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                "SUCCESS"
                            } else {
                                failureCount.incrementAndGet()
                                "FAILURE"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR: ${e.message}"
                        }
                    }, executor)
                }

                // 모든 요청 완료 대기
                CompletableFuture.allOf(*futures.toTypedArray()).join()

                // then
                successCount.get() shouldBe 1  // 정확히 하나의 예약만 성공
                failureCount.get() shouldBe 4  // 나머지 4개는 실패

                // 해당 사용자의 예약 확인
                val userReservations = reservationRepository.findAll().filter { it.userId == user.userId }
                userReservations.size shouldBe 1

                executor.shutdown()
            }
        }

        context("여러 좌석에 대해 동시에 예약 요청을 할 때") {
            it("각 좌석별로 정확히 하나의 예약만 성공해야 한다") {
                // given - 추가 좌석 생성
                val availableStatus = seatStatusTypeRepository.findByCode("AVAILABLE")!!
                val additionalSeats = (2..5).map { seatNumber ->
                    seatRepository.save(
                        Seat.Companion.create(
                            scheduleId = testSchedule.scheduleId,
                            seatNumber = "A$seatNumber",
                            price = BigDecimal("50000"),
                            availableStatus = availableStatus
                        )
                    )
                }

                val allSeats = listOf(testSeat) + additionalSeats
                val executor = Executors.newFixedThreadPool(20)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when - 각 좌석에 대해 여러 사용자가 동시 예약 시도
                val futures = allSeats.flatMapIndexed { seatIndex, seat ->
                    testUsers.take(4).mapIndexed { userIndex, user ->
                        CompletableFuture.supplyAsync({
                            try {
                                val request = ReservationCreateRequest(
                                    userId = user.userId,
                                    concertId = testConcert.concertId,
                                    seatId = seat.seatId,
                                    token = validTokens[userIndex].token
                                )

                                val result = mockMvc.perform(
                                    MockMvcRequestBuilders.post("/api/v1/reservations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request))
                                ).andReturn()

                                if (result.response.status == 201) {
                                    successCount.incrementAndGet()
                                    "SUCCESS-SEAT${seat.seatNumber}"
                                } else {
                                    failureCount.incrementAndGet()
                                    "FAILURE-SEAT${seat.seatNumber}"
                                }
                            } catch (e: Exception) {
                                failureCount.incrementAndGet()
                                "ERROR: ${e.message}"
                            }
                        }, executor)
                    }
                }

                // 모든 요청 완료 대기
                CompletableFuture.allOf(*futures.toTypedArray()).join()

                // then
                successCount.get() shouldBe 5  // 5개 좌석, 각각 하나씩 성공
                failureCount.get() shouldBe 15  // 나머지는 실패

                // 각 좌석별 예약 확인
                allSeats.forEach { seat ->
                    val seatReservations = reservationRepository.findAll().filter { it.seatId == seat.seatId }
                    seatReservations.size shouldBe 1
                }

                // 모든 좌석이 예약됨 상태인지 확인
                val reservedStatus = seatStatusTypeRepository.findByCode("RESERVED")!!
                allSeats.forEach { seat ->
                    val updatedSeat = seatRepository.findById(seat.seatId).orElseThrow { RuntimeException("Seat not found") }
                    updatedSeat.status.code shouldBe "RESERVED"
                }

                executor.shutdown()
            }
        }

        context("높은 부하 상황에서 예약 처리를 할 때") {
            it("데이터 일관성이 유지되어야 한다") {
                // given
                val executor = Executors.newFixedThreadPool(50)
                val requestCount = 100
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when - 100개의 동시 요청
                val futures = (1..requestCount).map { requestIndex ->
                    CompletableFuture.supplyAsync({
                        try {
                            val userIndex = requestIndex % testUsers.size
                            val user = testUsers[userIndex]
                            val token = validTokens[userIndex]

                            val request = ReservationCreateRequest(
                                userId = user.userId,
                                concertId = testConcert.concertId,
                                seatId = testSeat.seatId,
                                token = token.token
                            )

                            val result = mockMvc.perform(
                                MockMvcRequestBuilders.post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                "SUCCESS"
                            } else {
                                failureCount.incrementAndGet()
                                "FAILURE"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR: ${e.message}"
                        }
                    }, executor)
                }

                // 모든 요청 완료 대기
                CompletableFuture.allOf(*futures.toTypedArray()).join()

                // then
                successCount.get() shouldBe 1  // 정확히 하나만 성공
                failureCount.get() shouldBe 99  // 나머지는 실패

                // 데이터베이스 일관성 확인
                val totalReservations = reservationRepository.findAll()
                totalReservations.size shouldBe 1

                val finalSeat = seatRepository.findById(testSeat.seatId).orElseThrow { RuntimeException("Seat not found") }
                finalSeat.status.code shouldBe "RESERVED"

                executor.shutdown()
            }
        }
    }
})