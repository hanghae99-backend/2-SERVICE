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
import org.springframework.test.annotation.Commit
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
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

        // 데이터 정리 - 순서 중요 (외래키 제약 고려)
        reservationRepository.deleteAll()
        seatRepository.deleteAll()
        concertScheduleRepository.deleteAll()
        concertRepository.deleteAll()
        pointRepository.deleteAll()
        userRepository.deleteAll()

        // 테스트 데이터 설정
        // 여러 테스트용 사용자 생성
        val baseUserId = System.currentTimeMillis() + 3000
        testUsers = (0..9).map { index ->
            val userId = baseUserId + index
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
        
        // 데이터가 다른 트랜잭션에서 보이도록 잠시 대기
        Thread.sleep(100)
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
                        // 약간의 랜덤 지연을 추가하여 동시성을 더 정확하게 테스트
                        Thread.sleep((0..10).random().toLong())
                        
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
                println("Future results: ${futures.map { it.get() }}")
                
                // 예약 성공 여부 확인 - 최소 1개는 성공해야 함
                val actualSuccessCount = successCount.get()
                actualSuccessCount shouldBe 1  // 정확히 하나의 예약만 성공
                
                if (actualSuccessCount > 0) {
                    failureCount.get() shouldBe (testUsers.size - actualSuccessCount)  // 나머지는 실패
                }

                // 해당 좌석에 대한 예약 확인
                val seatReservations = reservationRepository.findAll().filter { it.seatId == testSeat.seatId }
                println("Total reservations for seat: ${seatReservations.size}")
                
                if (actualSuccessCount > 0) {
                    seatReservations.size shouldBe actualSuccessCount
                    
                    // 좌석 상태가 "예약됨"으로 변경되었는지 확인
                    val updatedSeat = seatRepository.findById(testSeat.seatId).orElseThrow { RuntimeException("Seat not found") }
                    println("Updated seat status: ${updatedSeat.status.code}")
                    updatedSeat.status.code shouldBe "RESERVED"
                }

                executor.shutdown()
            }
        }
    }
})
