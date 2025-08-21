package kr.hhplus.be.server.api.payment.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.config.ConcurrencyTest
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
import kr.hhplus.be.server.domain.payment.repositories.PaymentRepository
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.domain.user.repositories.UserRepository
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.test.utils.TestRedisUtils
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
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>
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

        // 분산락 상태 정리
        try {
            distributedLock.clearAllLocks()
        } catch (e: Exception) {
            // 무시
        }
        
        // Redis 정리
        TestRedisUtils.flushDatabase(redisTemplate)
        
        // DB 데이터 정리
        try {
            val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
            jdbcTemplate.execute("DELETE FROM payment")
            jdbcTemplate.execute("DELETE FROM reservation")
            jdbcTemplate.execute("DELETE FROM seat")
            jdbcTemplate.execute("DELETE FROM concert_schedule")
            jdbcTemplate.execute("DELETE FROM concert")
            jdbcTemplate.execute("DELETE FROM point_history")
            jdbcTemplate.execute("DELETE FROM point")
            jdbcTemplate.execute("DELETE FROM users")
        } catch (e: Exception) {
            // 무시
        }
        
        // 충분한 초기화 대기
        Thread.sleep(500)

        // 개별 테스트 사용자 생성 (고유한 userId 보장)
        testUsers = (1..5).map { index ->
            val user = userRepository.save(User(
                userId = (index+1).toLong(),
            ))
            userRepository.flush()

            // 포인트 생성
            pointRepository.save(Point.create(user.userId, BigDecimal("200000")))
            pointRepository.flush()

            user
        }

        // 포인트 이력 타입
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
                title = "결제 테스트 콘서트",
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

        // 좌석 상태 타입
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

        // 좌석 생성 (사용 가능 상태로)
        testSeat = seatRepository.save(
            Seat.create(
                scheduleId = testSchedule.scheduleId,
                seatNumber = "A1",
                price = BigDecimal("50000"),
                availableStatus = availableStatus
            )
        )

        // 예약 상태 타입
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

        // 결제 상태 타입
        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = "PEND",
                name = "대기중",
                description = "결제 처리 대기중"
            )
        )

        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = "COMP",
                name = "결제 완료",
                description = "결제가 성공적으로 완료됨"
            )
        )

        // FAILED 상태 추가 (에러 처리용)
        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = "FAIL",
                name = "결제 실패",
                description = "결제 처리 실패"
            )
        )

        // 각 사용자별로 개별 좌석과 예약 생성
        testReservations = testUsers.mapIndexed { index, user ->
            // 각 사용자별로 다른 좌석 생성
            val userSeat = seatRepository.save(
                Seat.create(
                    scheduleId = testSchedule.scheduleId,
                    seatNumber = "A${index + 1}",
                    price = BigDecimal("50000"),
                    availableStatus = availableStatus
                )
            )
            seatRepository.flush()
            
            // 좌석 예약 처리 (상태를 RESERVED로 변경)
            userSeat.reserve(reservedStatus)
            seatRepository.save(userSeat)
            seatRepository.flush()
            
            // 예약 생성
            val reservation = reservationRepository.save(
                Reservation.createTemporary(
                    userId = user.userId,
                    concertId = testConcert.concertId,
                    seatId = userSeat.seatId,
                    seatNumber = userSeat.seatNumber,
                    price = userSeat.price,
                    temporaryStatus = temporaryStatus,
                    tempMinutes = 10
                )
            )
            reservationRepository.flush()
            reservation
        }

        // 토큰 생성
        testTokens = testUsers.map { user ->
            val token = tokenFactory.createWaitingToken(user.userId)
            tokenStore.save(token)
            tokenStore.activateToken(token.token)
            token
        }
        
        // 데이터 정합성 확인을 위한 대기
        Thread.sleep(500)
        
        // 모든 리포지토리 flush
        userRepository.flush()
        pointRepository.flush()
        concertRepository.flush()
        concertScheduleRepository.flush()
        seatRepository.flush()
        reservationRepository.flush()
        
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