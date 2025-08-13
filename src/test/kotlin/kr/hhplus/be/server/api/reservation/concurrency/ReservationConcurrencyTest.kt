package kr.hhplus.be.server.api.reservation.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.config.ConcurrencyTest
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
import kr.hhplus.be.server.config.TestDataCleanupService
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class ConcurrencyTestData(
    val users: List<User>,
    val concert: Concert,
    val schedule: ConcertSchedule,
    val seat: Seat,
    val tokens: List<WaitingToken>,
    val temporaryStatus: ReservationStatusType,
    val availableStatus: SeatStatusType,
    val reservedStatus: SeatStatusType
)

@ConcurrencyTest
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
    private val tokenFactory: TokenFactory,
    private val testDataCleanupService: TestDataCleanupService
) : DescribeSpec() {

    private lateinit var mockMvc: MockMvc

    private fun createConcurrencyTestData(): ConcurrencyTestData {
        // 고유한 ID 생성
        val timestamp = System.nanoTime()
        val baseUserId = timestamp % 1000000

        println("동시성 테스트 데이터 생성 시작 - timestamp: $timestamp")

        // 여러 테스트용 사용자 생성
        val testUsers = (0..9).map { index ->
            val userId = baseUserId + index
            val user = userRepository.save(User.create(userId))
            // 각 사용자에게 충분한 포인트 부여
            pointRepository.save(Point.create(userId, BigDecimal("500000")))
            println("사용자 생성 완료 - userId: $userId")
            user
        }

        // 콘서트 생성
        val testConcert = concertRepository.save(
            Concert.create(
                title = "동시성 테스트 콘서트 $timestamp",
                artist = "테스트 아티스트 $timestamp"
            )
        )
        println("콘서트 생성 완료 - concertId: ${testConcert.concertId}")

        // 콘서트 스케줄 생성
        val testSchedule = concertScheduleRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(30),
                venue = "동시성 테스트 공연장 $timestamp",
                totalSeats = 1
            )
        )
        println("스케줄 생성 완료 - scheduleId: ${testSchedule.scheduleId}")

        // 좌석 상태 타입 생성
        val availableStatus = seatStatusTypeRepository.findByCode("AVAILABLE")
            ?: seatStatusTypeRepository.save(
                SeatStatusType(
                    code = "AVAILABLE",
                    name = "예약 가능",
                    description = "예약 가능한 좌석"
                )
            )

        val reservedStatus = seatStatusTypeRepository.findByCode("RESERVED")
            ?: seatStatusTypeRepository.save(
                SeatStatusType(
                    code = "RESERVED",
                    name = "예약됨",
                    description = "예약된 좌석"
                )
            )

        // 단일 좌석 생성 (동시성 테스트용)
        val testSeat = seatRepository.save(
            Seat.create(
                scheduleId = testSchedule.scheduleId,
                seatNumber = "A1",
                price = BigDecimal("50000"),
                availableStatus = availableStatus
            )
        )
        println("좌석 생성 완료 - seatId: ${testSeat.seatId}, 상태: ${testSeat.status.code}")

        // 예약 상태 타입 생성
        val temporaryStatus = reservationStatusTypeRepository.findByCode("TEMPORARY")
            ?: reservationStatusTypeRepository.save(
                ReservationStatusType(
                    code = "TEMPORARY",
                    name = "임시 예약",
                    description = "임시 예약 상태"
                )
            )

        // 각 사용자에 대한 유효한 토큰 생성
        val validTokens = testUsers.map { user ->
            val token = tokenFactory.createWaitingToken(user.userId)
            tokenStore.save(token)
            tokenStore.activateToken(token.token)
            
            // 토큰 유효성 검증
            val isValid = tokenStore.validate(token.token)
            if (!isValid) {
                throw IllegalStateException("토큰 활성화 실패: ${token.token}")
            }
            println("토큰 생성 및 활성화 완료 - userId: ${user.userId}, token: ${token.token}")
            token
        }

        println("동시성 테스트 데이터 생성 완료")
        return ConcurrencyTestData(
            users = testUsers,
            concert = testConcert,
            schedule = testSchedule,
            seat = testSeat,
            tokens = validTokens,
            temporaryStatus = temporaryStatus,
            availableStatus = availableStatus,
            reservedStatus = reservedStatus
        )
    }

    init {
        extension(SpringExtension)

        beforeSpec {
            mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build()
        }

        afterEach {
            // 각 테스트 후 데이터 정리
            try {
                testDataCleanupService.cleanupAllTestData()
                println("테스트 데이터 정리 완료")
            } catch (e: Exception) {
                println("테스트 데이터 정리 실패: ${e.message}")
            }
        }

        describe("예약 동시성 테스트") {
            context("동일한 좌석에 대해 여러 사용자가 동시에 예약 요청을 할 때") {
                it("하나의 예약만 성공하고 나머지는 실패해야 한다") {
                    // given
                    testDataCleanupService.cleanupAllTestData()
                    val testData = createConcurrencyTestData()
                    
                    // 데이터 검증
                    println("테스트 데이터 검증:")
                    println("- 사용자 수: ${testData.users.size}")
                    println("- 콘서트 ID: ${testData.concert.concertId}")
                    println("- 스케줄 ID: ${testData.schedule.scheduleId}")
                    println("- 좌석 ID: ${testData.seat.seatId}, 상태: ${testData.seat.status.code}")
                    println("- 토큰 수: ${testData.tokens.size}")

                    val executor = Executors.newFixedThreadPool(10)
                    val successCount = AtomicInteger(0)
                    val failureCount = AtomicInteger(0)

                    // when
                    val futures = testData.users.mapIndexed { index, user ->
                        CompletableFuture.supplyAsync({
                            try {
                                // 약간의 랜덤 지연을 추가하여 동시성을 더 정확하게 테스트
                                Thread.sleep((0..10).random().toLong())
                                
                                println("[예약 시도] userId: ${user.userId}, seatId: ${testData.seat.seatId}")
                                
                                val request = ReservationCreateRequest(
                                    userId = user.userId,
                                    concertId = testData.concert.concertId,
                                    seatId = testData.seat.seatId,
                                    token = testData.tokens[index].token
                                )

                                val result = mockMvc.perform(
                                    MockMvcRequestBuilders.post("/api/v1/reservations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request))
                                ).andReturn()

                                println("[예약 결과] userId: ${user.userId}, status: ${result.response.status}, response: ${result.response.contentAsString}")

                                if (result.response.status == 201) {
                                    successCount.incrementAndGet()
                                    println("[예약 성공] userId: ${user.userId}")
                                    "SUCCESS"
                                } else {
                                    failureCount.incrementAndGet()
                                    println("[예약 실패] userId: ${user.userId}, status: ${result.response.status}")
                                    "FAILURE"
                                }
                            } catch (e: Exception) {
                                failureCount.incrementAndGet()
                                println("[예약 예외] userId: ${user.userId}, error: ${e.message}")
                                e.printStackTrace()
                                "ERROR: ${e.message}"
                            }
                        }, executor)
                    }

                    // 모든 요청 완료 대기
                    val results = try {
                        CompletableFuture.allOf(*futures.toTypedArray()).get(30, TimeUnit.SECONDS)
                        futures.map { it.get() }
                    } catch (e: Exception) {
                        println("Error waiting for futures: ${e.message}")
                        futures.map { 
                            try { it.get() } catch (ex: Exception) { "ERROR: ${ex.message}" }
                        }
                    }

                    // then
                    println("Reservation test - Success: ${successCount.get()}, Failure: ${failureCount.get()}")
                    println("Future results: $results")
                    
                    // 예약 성공 여부 확인 - 최소 1개는 성공해야 함
                    val actualSuccessCount = successCount.get()
                    actualSuccessCount shouldBe 1  // 정확히 하나의 예약만 성공
                    
                    if (actualSuccessCount > 0) {
                        failureCount.get() shouldBe (testData.users.size - actualSuccessCount)  // 나머지는 실패
                    }

                    // 해당 좌석에 대한 예약 확인
                    val seatReservations = reservationRepository.findAll().filter { it.seatId == testData.seat.seatId }
                    println("Total reservations for seat: ${seatReservations.size}")
                    
                    if (actualSuccessCount > 0) {
                        seatReservations.size shouldBe actualSuccessCount
                        
                        // 좌석 상태가 "예약됨"으로 변경되었는지 확인
                        val updatedSeat = seatRepository.findById(testData.seat.seatId)
                        if (updatedSeat != null) {
                            println("Updated seat status: ${updatedSeat.status.code}")
                            updatedSeat.status.code shouldBe "RESERVED"
                        }
                    }

                    executor.shutdown()
                    executor.awaitTermination(5, TimeUnit.SECONDS)
                }
            }
        }
    }
}
