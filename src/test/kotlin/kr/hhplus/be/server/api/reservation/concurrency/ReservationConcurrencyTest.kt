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
import kr.hhplus.be.server.domain.concert.models.*
import kr.hhplus.be.server.domain.concert.repositories.*
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.domain.user.repositories.UserRepository
import kr.hhplus.be.server.global.lock.DistributedLock
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ConcurrencyTest
class ReservationConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatRepository: SeatRepository,
    private val seatStatusTypeRepository: SeatStatusTypePojoRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationStatusTypeRepository: ReservationStatusTypePojoRepository,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUsers: List<User>
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testSeats: List<Seat>
    lateinit var testTokens: List<WaitingToken>
    lateinit var availableStatus: SeatStatusType
    lateinit var reservedStatus: SeatStatusType

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 데이터 정리
        try {
            val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
            // 외래키 관계를 고려한 순서로 삭제
            jdbcTemplate.execute("DELETE FROM point_history")
            jdbcTemplate.execute("DELETE FROM payment")
            jdbcTemplate.execute("DELETE FROM reservation")
            jdbcTemplate.execute("DELETE FROM seat")
            jdbcTemplate.execute("DELETE FROM concert_schedule")
            jdbcTemplate.execute("DELETE FROM concert")
            jdbcTemplate.execute("DELETE FROM point")
            jdbcTemplate.execute("DELETE FROM users")
            
            // 시퀀스 초기화
            jdbcTemplate.execute("ALTER SEQUENCE users_user_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE concert_concert_id_seq RESTART WITH 1")
        } catch (e: Exception) {
            // 무시
        }
        
        // Redis 캐시 정리 - 안전한 방법으로 초기화
        try {
            redisTemplate.connectionFactory?.connection?.use { connection ->
                connection.serverCommands().flushDb()
            }
        } catch (e: Exception) {
            println("Redis 캐시 정리 실패: ${e.message}")
            // fallback: 개별 키 삭제 시도
            try {
                redisTemplate.delete(redisTemplate.keys("*") ?: emptySet())
            } catch (fallbackError: Exception) {
                println("Redis 개별 키 삭제도 실패: ${fallbackError.message}")
            }
        }
        
        // 분산락 통계 초기화
        distributedLock.resetStatistics()

        // 테스트 사용자 생성
        testUsers = (0..9).map { index ->
            val user = userRepository.save(User(
                userId = (index+1).toLong(),
            ))
            userRepository.flush()
            user
        }
        
        // 사용자별 포인트 초기화 (결제를 위한 준비)
        try {
            val pointRepository = webApplicationContext.getBean("pointRepository", 
                kr.hhplus.be.server.domain.balance.repositories.PointRepository::class.java)
            testUsers.forEach { user ->
                val point = kr.hhplus.be.server.domain.balance.models.Point.create(
                    user.userId, 
                    BigDecimal("1000000") // 100만 포인트 초기 지급
                )
                pointRepository.save(point)
            }
            pointRepository.flush()
        } catch (e: Exception) {
            // 무시
        }

        // 콘서트 생성
        testConcert = concertRepository.save(
            Concert.create(
                title = "예약 동시성 테스트 콘서트",
                artist = "테스트 아티스트"
            )
        )

        // 스케줄 생성
        testSchedule = concertScheduleRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(30),
                venue = "테스트 공연장",
                totalSeats = 50
            )
        )

        // 좌석 상태 타입 생성
        availableStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = SeatStatusType.AVAILABLE,
                name = "예약가능",
                description = "예약 가능한 좌석"
            )
        )

        reservedStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = SeatStatusType.RESERVED,
                name = "예약완료",
                description = "예약된 좌석"
            )
        )

        // 테스트 좌석들 생성
        testSeats = (1..10).map { seatNum ->
            seatRepository.save(
                Seat.create(
                    scheduleId = testSchedule.scheduleId,
                    seatNumber = "A$seatNum",
                    price = BigDecimal("100000"),
                    availableStatus = availableStatus
                )
            )
        }
        seatRepository.flush()

        // 예약 상태 타입 생성
        reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = ReservationStatusType.TEMPORARY,
                name = "임시예약",
                description = "임시 예약 상태"
            )
        )

        reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = ReservationStatusType.CONFIRMED,
                name = "확정예약",
                description = "결제 완료된 확정 예약"
            )
        )

        // 토큰 생성 및 활성화
        testTokens = testUsers.map { user ->
            val token = tokenFactory.createWaitingToken(user.userId)
            tokenStore.save(token)
            tokenStore.activateToken(token.token)
            token
        }
        
        Thread.sleep(100)
    }

    afterEach {
        val stats = distributedLock.getLockStatistics()
        println("""
            === Reservation 동시성 테스트 분산락 통계 ===
            성공: ${stats.acquisitionCount}
            실패: ${stats.failureCount}
            평균 대기시간: ${stats.averageWaitTimeMs}ms
            성공률: ${stats.successRate}%
        """.trimIndent())
    }

    describe("예약 생성 동시성 테스트 - 분산락 적용") {
        context("여러 사용자가 동시에 같은 좌석을 예약하려고 할 때") {
            it("분산락으로 하나의 예약만 성공해야 한다") {
                // given
                val targetSeat = testSeats[0]
                val executor = Executors.newFixedThreadPool(testUsers.size)
                val latch = CountDownLatch(testUsers.size)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when
                val futures = testUsers.mapIndexed { index, user ->
                    CompletableFuture.supplyAsync({
                        try {
                            latch.countDown()
                            latch.await(10, TimeUnit.SECONDS) // 동시 시작

                            val request = ReservationCreateRequest(
                                userId = user.userId,
                                concertId = testConcert.concertId,
                                seatId = targetSeat.seatId,
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
                                "FAILURE: ${result.response.status}"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR: ${e.message}"
                        }
                    }, executor)
                }

                val results = futures.map { it.get(60, TimeUnit.SECONDS) }

                // then
                println("=== 같은 좌석 예약 동시성 결과 ===")
                println("성공: ${successCount.get()}, 실패: ${failureCount.get()}")
                results.forEach { println(it) }

                // 하나만 성공해야 함
                successCount.get() shouldBe 1
                failureCount.get() shouldBe (testUsers.size - 1)

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        context("여러 사용자가 서로 다른 좌석을 동시에 예약할 때") {
            it("모든 예약이 성공해야 한다") {
                // given
                val executor = Executors.newFixedThreadPool(testUsers.size)
                val latch = CountDownLatch(testUsers.size)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when - 각 사용자가 다른 좌석 예약
                val futures = testUsers.mapIndexed { index, user ->
                    CompletableFuture.supplyAsync({
                        try {
                            latch.countDown()
                            latch.await(10, TimeUnit.SECONDS)

                            val request = ReservationCreateRequest(
                                userId = user.userId,
                                concertId = testConcert.concertId,
                                seatId = testSeats[index].seatId, // 각자 다른 좌석
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
                                "FAILURE: ${result.response.status}"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR: ${e.message}"
                        }
                    }, executor)
                }

                val results = futures.map { it.get(60, TimeUnit.SECONDS) }

                // then
                println("=== 다른 좌석 예약 동시성 결과 ===")
                println("성공: ${successCount.get()}, 실패: ${failureCount.get()}")
                results.forEach { println(it) }

                // 모두 성공해야 함 (서로 다른 좌석)
                successCount.get() shouldBe testUsers.size
                failureCount.get() shouldBe 0

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})